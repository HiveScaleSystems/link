package net.beehivesys.link.hytale;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import net.beehivesys.link.Link;
import net.beehivesys.link.LinkConfig;
import net.beehivesys.link.LinkException;
import net.beehivesys.link.LinkLog;
import net.beehivesys.link.ticket.Ticket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks every connecting player's referral data. A valid ticket makes the connection a transfer
 * (and may pick the world to land in); anything else is a direct join, which a server with
 * {@code requireTicket} bounces to its fallback group.
 */
final class ArrivalListener {

    private final Link link;
    private final LinkLog log;
    /** Verified arrivals between the handshake and the world join, keyed by player. */
    private final Map<UUID, Ticket.Claims> arriving = new ConcurrentHashMap<>();

    ArrivalListener(final Link link, final LinkLog log) {
        this.link = link;
        this.log = log;
    }

    void register(final EventRegistry events) {
        events.registerGlobal(PlayerSetupConnectEvent.class, this::onSetupConnect);
        events.registerGlobal(PlayerConnectEvent.class, this::onConnect);
        events.registerGlobal(PlayerDisconnectEvent.class,
                event -> arriving.remove(event.getPlayerRef().getUuid()));
    }

    private void onSetupConnect(final PlayerSetupConnectEvent event) {
        final Ticket.Claims claims = link.admit(event.getReferralData(), event.getUuid());
        if (claims != null) {
            arriving.put(event.getUuid(), claims);
            final long left = Math.max(0, (claims.expiresAt() - System.currentTimeMillis()) / 1000);
            log.info("[Link] ticket from " + claims.source() + " for " + event.getUsername()
                    + ": signed, addressed here, " + left + "s left, first use");
            return;
        }
        if (event.isReferralConnection()) {
            // Not saying which check failed: the sender is whoever is connecting.
            log.warn("[Link] Refused a transfer ticket from " + event.getUsername());
        }

        final LinkConfig config = link.config();
        if (!config.requireTicket || config.fallbackGroup.equalsIgnoreCase(link.self().group())) {
            return;
        }
        try {
            final Link.Route route = link.routeToGroup(event.getUuid(), config.fallbackGroup);
            event.referToServer(route.server().host(), route.server().port(), route.ticket());
        } catch (final LinkException e) {
            event.setReason(Message.raw("This server is only reachable through the lobby. " + e.getMessage()));
            event.setCancelled(true);
        }
    }

    private void onConnect(final PlayerConnectEvent event) {
        final Ticket.Claims claims = arriving.remove(event.getPlayerRef().getUuid());
        if (claims == null || !claims.hasWorld()) {
            return;
        }
        final World world = Universe.get().getWorld(claims.world());
        if (world != null) {
            event.setWorld(world);
        } else {
            log.warn("[Link] " + event.getPlayerRef().getUsername() + " was sent to world " + claims.world()
                    + ", which does not exist here; using the default world");
        }
    }
}
