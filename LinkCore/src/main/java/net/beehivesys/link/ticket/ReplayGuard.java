package net.beehivesys.link.ticket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers ticket ids this server has already admitted until they expire, so a ticket is good for
 * one arrival. Local memory is enough: a ticket names its destination, so only this server could
 * ever see it twice.
 */
public final class ReplayGuard {

    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    /** @return true the first time an id is presented, false on every later attempt */
    public boolean firstUse(final Ticket.Claims claims) {
        final long now = System.currentTimeMillis();
        if (seen.size() > 1024) {
            seen.values().removeIf(expiry -> expiry < now);
        }
        return seen.putIfAbsent(claims.id(), claims.expiresAt() + Ticket.CLOCK_SKEW_MILLIS) == null;
    }
}
