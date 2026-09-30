package net.beehivesys.link;

import java.util.List;

/**
 * One server in the network, as the registry sees it.
 *
 * @param id         unique within the network, and the name tickets are addressed to
 * @param group      what matchmaking picks from, e.g. {@code lobby} or {@code skywars}
 * @param host       the address players are referred to, as reachable from the player's client
 * @param players    online players at the last heartbeat, or -1 when unknown (static lists)
 * @param maxPlayers -1 when unknown
 * @param lastSeen   epoch millis of the last heartbeat, or 0 for a static entry that never reports
 * @param online     who is online, at the last heartbeat. Empty when unknown: see {@link #reportsLoad()}
 */
public record LinkServer(String id, String group, String host, int port,
                         int players, int maxPlayers, long lastSeen, List<LinkPlayer> online) {

    public LinkServer {
        online = online == null ? List.of() : List.copyOf(online);
    }

    /** A server whose player list is not known, like another server in a static network. */
    public LinkServer(final String id, final String group, final String host, final int port,
                      final int players, final int maxPlayers, final long lastSeen) {
        this(id, group, host, port, players, maxPlayers, lastSeen, List.of());
    }

    /** True when this server reports its players: then {@link #players()} and {@link #online()} are real. */
    public boolean reportsLoad() {
        return players >= 0 && maxPlayers > 0;
    }

    public boolean isFull() {
        return reportsLoad() && players >= maxPlayers;
    }

    public LinkServer withPlayers(final int count) {
        return new LinkServer(id, group, host, port, count, maxPlayers, lastSeen, online);
    }
}
