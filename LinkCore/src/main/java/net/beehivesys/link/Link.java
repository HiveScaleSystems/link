package net.beehivesys.link;

import net.beehivesys.link.backend.HttpBackend;
import net.beehivesys.link.backend.NetworkBackend;
import net.beehivesys.link.backend.RedisBackend;
import net.beehivesys.link.backend.StaticBackend;
import net.beehivesys.link.ticket.ReplayGuard;
import net.beehivesys.link.ticket.Ticket;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * One server's view of the network: who is out there, and the tickets to get players between them.
 * The engine plugin owns the players and the referral call; this owns everything else.
 *
 * <p>The registry is read on a timer and cached, so nothing a player does waits on the network.
 * When the registry is unreachable the last known list keeps serving: transfers to servers that are
 * still up keep working through a Redis or Worker outage.
 */
public final class Link implements AutoCloseable {

    static final long SYNC_INTERVAL_MILLIS = 3_000L;
    /**
     * An HTTP registry is billed per request (the Cloudflare Worker is), so it gets a slower
     * heartbeat. Matchmaking stays accurate between beats because {@link #countArrival} counts the
     * players we send locally.
     */
    static final long HTTP_SYNC_INTERVAL_MILLIS = 10_000L;

    private final LinkConfig config;
    private final NetworkBackend backend;
    private final LinkLog log;
    private final IntSupplier onlinePlayers;
    private final int maxPlayers;
    private final Matchmaker matchmaker = new Matchmaker();
    private final ReplayGuard replayGuard = new ReplayGuard();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread thread = new Thread(r, "link-sync");
        thread.setDaemon(true);
        return thread;
    });

    private volatile String secret;
    private volatile List<LinkServer> servers = List.of();
    private volatile boolean registryDown;

    /** A player's way to another server: where to refer them, and the ticket to carry. */
    public record Route(LinkServer server, byte[] ticket) {
    }

    public Link(final LinkConfig config, final NetworkBackend backend, final LinkLog log,
                final IntSupplier onlinePlayers, final int maxPlayers) {
        this.config = config;
        this.backend = backend;
        this.log = log;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
    }

    public static NetworkBackend backendFor(final LinkConfig config) {
        return switch (config.backend) {
            case STATIC -> new StaticBackend(config);
            case REDIS -> new RedisBackend(config.redis);
            case HTTP -> new HttpBackend(config.http);
        };
    }

    /**
     * Joins the network. A registry that is down at boot does not stop the server: Link keeps
     * retrying in the background and transfers start working once it answers.
     */
    public void start() {
        tick();
        final long interval = config.backend == LinkConfig.Backend.HTTP ? HTTP_SYNC_INTERVAL_MILLIS : SYNC_INTERVAL_MILLIS;
        scheduler.scheduleWithFixedDelay(this::tick, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            if (secret == null) {
                final String fetched = backend.secret();
                if (fetched == null || fetched.isBlank()) {
                    throw new IllegalStateException("the registry has no network secret");
                }
                secret = fetched;
            }
            servers = backend.sync(self());
            if (registryDown) {
                log.info("[Link] Registry reachable again (" + servers.size() + " servers)");
                registryDown = false;
            }
        } catch (final Exception e) {
            // Logged once per outage, not every tick.
            if (!registryDown) {
                log.warn("[Link] Registry unreachable, keeping the last known server list: " + e.getMessage());
                registryDown = true;
            }
        }
    }

    public LinkServer self() {
        final String host = config.host;
        final int port = config.port;
        if (config.backend == LinkConfig.Backend.STATIC) {
            for (final LinkConfig.StaticServer s : config.servers) {
                if (s.id.equals(config.serverId)) {
                    return new LinkServer(s.id, s.group, s.host, s.port, onlinePlayers.getAsInt(), maxPlayers,
                            System.currentTimeMillis());
                }
            }
        }
        return new LinkServer(config.serverId, config.group, host, port, onlinePlayers.getAsInt(), maxPlayers,
                System.currentTimeMillis());
    }

    public boolean isReady() {
        return secret != null;
    }

    public boolean isRegistryDown() {
        return registryDown;
    }

    public List<LinkServer> servers() {
        return servers;
    }

    public List<LinkServer> servers(final String group) {
        return servers.stream().filter(s -> s.group().equalsIgnoreCase(group)).toList();
    }

    public LinkServer server(final String id) {
        return servers.stream().filter(s -> s.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    /** A route to one named server, optionally into a specific world there. */
    public Route routeTo(final UUID player, final String serverId, final String world) throws LinkException {
        final LinkServer target = server(serverId);
        if (target == null) {
            throw new LinkException("There is no server called " + serverId + ".");
        }
        if (target.id().equals(config.serverId)) {
            throw new LinkException("You are already on " + target.id() + ".");
        }
        if (target.isFull()) {
            throw new LinkException(target.id() + " is full.");
        }
        return route(player, target, world);
    }

    /** A route to the best server in a group, per the configured strategy. */
    public Route routeToGroup(final UUID player, final String group) throws LinkException {
        final LinkServer target = matchmaker.pick(servers, group, config.serverId, config.strategy);
        if (target == null) {
            throw new LinkException(servers(group).isEmpty()
                    ? "There are no " + group + " servers."
                    : "Every " + group + " server is full.");
        }
        return route(player, target, null);
    }

    private Route route(final UUID player, final LinkServer target, final String world) throws LinkException {
        final String key = secret;
        if (key == null) {
            throw new LinkException("Transfers are not available yet, try again in a moment.");
        }
        countArrival(target.id());
        final String ticket = Ticket.mint(key, Ticket.claims(player, config.serverId, target.id(), world));
        return new Route(target, ticket.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Counts a player we just sent into our cached list, so a burst of matchmaking between two
     * heartbeats does not pile everyone onto the same server. The next sync replaces the guess.
     */
    private void countArrival(final String serverId) {
        final List<LinkServer> updated = new ArrayList<>(servers.size());
        for (final LinkServer s : servers) {
            updated.add(s.id().equals(serverId) && s.reportsLoad() ? s.withPlayers(s.players() + 1) : s);
        }
        servers = List.copyOf(updated);
    }

    /**
     * Checks the referral data a connecting player carried.
     *
     * @return the ticket's claims, or null when there is no valid ticket for this player here. Null
     *         means "a direct connection", never "a transfer".
     */
    public Ticket.Claims admit(final byte[] referralData, final UUID player) {
        final String key = secret;
        if (key == null || referralData == null || referralData.length == 0) {
            return null;
        }
        final Ticket.Claims claims = Ticket.verify(key, new String(referralData, StandardCharsets.UTF_8),
                config.serverId, player);
        return claims != null && replayGuard.firstUse(claims) ? claims : null;
    }

    public LinkConfig config() {
        return config;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            // A heartbeat still in flight would re-register us after the leave below.
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            backend.leave(config.serverId);
        } catch (final Exception e) {
            log.warn("[Link] Could not leave the registry cleanly: " + e.getMessage());
        }
        backend.close();
    }
}
