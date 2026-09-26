package net.beehivesys.link;

/**
 * One server in the network, as the registry sees it.
 *
 * @param id         unique within the network, and the name tickets are addressed to
 * @param group      what matchmaking picks from, e.g. {@code lobby} or {@code skywars}
 * @param host       the address players are referred to, as reachable from the player's client
 * @param players    online players at the last heartbeat, or -1 when unknown (static lists)
 * @param maxPlayers -1 when unknown
 * @param lastSeen   epoch millis of the last heartbeat, or 0 for a static entry that never reports
 */
public record LinkServer(String id, String group, String host, int port,
                         int players, int maxPlayers, long lastSeen) {

    public boolean reportsLoad() {
        return players >= 0 && maxPlayers > 0;
    }

    public boolean isFull() {
        return reportsLoad() && players >= maxPlayers;
    }

    public LinkServer withPlayers(final int count) {
        return new LinkServer(id, group, host, port, count, maxPlayers, lastSeen);
    }
}
