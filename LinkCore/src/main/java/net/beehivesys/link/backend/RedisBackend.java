package net.beehivesys.link.backend;

import net.beehivesys.link.LinkConfig;
import net.beehivesys.link.LinkServer;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * A Redis the operator already runs. Every server gets a key that expires {@link #TTL_SECONDS} after
 * its last heartbeat, so liveness uses Redis's clock and not the servers', which may disagree.
 *
 * <pre>
 *   {ns}:secret         the network secret, created by whichever server boots first
 *   {ns}:servers        set of server ids that have ever registered
 *   {ns}:server:{id}    that server's JSON, expiring with its heartbeat
 * </pre>
 *
 * Whoever can write to these keys can join the network and sign tickets, so the Redis must not be
 * reachable without a password.
 */
public final class RedisBackend implements NetworkBackend {

    static final int TTL_SECONDS = 15;

    private final JedisPooled redis;
    private final String ns;

    public RedisBackend(final LinkConfig.Redis config) {
        this.redis = new JedisPooled(URI.create(config.url));
        this.ns = config.namespace == null || config.namespace.isBlank() ? "link" : config.namespace;
    }

    @Override
    public String secret() {
        redis.set(ns + ":secret", LinkConfig.generateSecret(), SetParams.setParams().nx());
        return redis.get(ns + ":secret");
    }

    @Override
    public List<LinkServer> sync(final LinkServer self) {
        redis.set(ns + ":server:" + self.id(), ServerJson.toJson(self).toString(),
                SetParams.setParams().ex(TTL_SECONDS));
        redis.sadd(ns + ":servers", self.id());

        final List<String> ids = new ArrayList<>(redis.smembers(ns + ":servers"));
        if (ids.isEmpty()) {
            return List.of();
        }
        final List<String> values = redis.mget(ids.stream().map(id -> ns + ":server:" + id).toArray(String[]::new));
        final List<LinkServer> live = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            final String raw = values.get(i);
            if (raw == null) {
                redis.srem(ns + ":servers", ids.get(i));
                continue;
            }
            try {
                live.add(ServerJson.parse(raw));
            } catch (final RuntimeException ignored) {
                // One server writing garbage must not take matchmaking down for the rest.
            }
        }
        return live;
    }

    @Override
    public void leave(final String serverId) {
        redis.del(ns + ":server:" + serverId);
        redis.srem(ns + ":servers", serverId);
    }

    @Override
    public void close() {
        redis.close();
    }
}
