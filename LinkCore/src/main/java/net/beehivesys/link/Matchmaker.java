package net.beehivesys.link;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Picks one server out of a group. Pure, so it can be tested without a network. */
public final class Matchmaker {

    private final AtomicInteger roundRobin = new AtomicInteger();

    /**
     * @param exclude usually this server: a player asking to play a group is asking to go somewhere else
     * @return the chosen server, or null when every server in the group is full or none exist
     */
    public LinkServer pick(final List<LinkServer> servers, final String group, final String exclude,
                           final LinkConfig.Strategy strategy) {
        final List<LinkServer> open = servers.stream()
                .filter(s -> s.group().equalsIgnoreCase(group))
                .filter(s -> !s.id().equals(exclude))
                .filter(s -> !s.isFull())
                .toList();
        if (open.isEmpty()) {
            return null;
        }
        // Nothing reports load (a static list): the best we can do is take turns.
        if (open.stream().noneMatch(LinkServer::reportsLoad)) {
            return open.get(Math.floorMod(roundRobin.getAndIncrement(), open.size()));
        }

        final Comparator<LinkServer> byLoad = Comparator.comparingDouble(Matchmaker::load);
        final Comparator<LinkServer> order = strategy == LinkConfig.Strategy.FILL ? byLoad.reversed() : byLoad;
        final LinkServer best = open.stream().min(order).orElseThrow();
        // Break ties at random, or every server in a quiet group would pick the same target.
        final List<LinkServer> tied = open.stream().filter(s -> load(s) == load(best)).toList();
        return tied.get(ThreadLocalRandom.current().nextInt(tied.size()));
    }

    private static double load(final LinkServer server) {
        return server.reportsLoad() ? (double) server.players() / server.maxPlayers() : 0.0;
    }
}
