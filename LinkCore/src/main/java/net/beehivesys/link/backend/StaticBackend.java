package net.beehivesys.link.backend;

import net.beehivesys.link.LinkConfig;
import net.beehivesys.link.LinkServer;

import java.util.List;

/**
 * The network is whatever {@code link.json} lists. No infrastructure at all, at the price of not
 * knowing whether the other servers are up, how full they are or who is on them: matchmaking
 * round-robins, and a referral to a server that is down fails on the player's client.
 */
public final class StaticBackend implements NetworkBackend {

    private final LinkConfig config;

    public StaticBackend(final LinkConfig config) {
        this.config = config;
    }

    @Override
    public String secret() {
        return config.secret;
    }

    @Override
    public List<LinkServer> sync(final LinkServer self) {
        return config.servers.stream()
                .map(s -> s.id.equals(self.id())
                        ? new LinkServer(s.id, s.group, s.host, s.port, self.players(), self.maxPlayers(), 0L, self.online())
                        : new LinkServer(s.id, s.group, s.host, s.port, -1, -1, 0L))
                .toList();
    }

    @Override
    public void leave(final String serverId) {
    }

    @Override
    public void close() {
    }
}
