package net.beehivesys.link.ticket;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every case here is something a connecting client could try, since the ticket rides the client. */
class TicketTest {

    private static final String SECRET = "network-secret";
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static String ticketTo(final String target) {
        return Ticket.mint(SECRET, Ticket.claims(PLAYER, "lobby-1", target, "arena"));
    }

    @Test
    void aTicketForThisServerAndPlayerIsAccepted() {
        final Ticket.Claims claims = Ticket.verify(SECRET, ticketTo("game-1"), "game-1", PLAYER);

        assertNotNull(claims);
        assertEquals("lobby-1", claims.source());
        assertEquals("arena", claims.world());
    }

    @Test
    void anotherNetworksSecretIsRefused() {
        assertNull(Ticket.verify("other-secret", ticketTo("game-1"), "game-1", PLAYER));
    }

    @Test
    void aTicketForAnotherServerIsRefusedEvenThoughTheSecretIsShared() {
        assertNull(Ticket.verify(SECRET, ticketTo("game-2"), "game-1", PLAYER));
    }

    @Test
    void aTicketCannotBeHandedToAnotherPlayer() {
        assertNull(Ticket.verify(SECRET, ticketTo("game-1"), "game-1", OTHER));
    }

    @Test
    void anExpiredTicketIsRefusedOnceTheSkewAllowanceIsUsedUp() {
        final String stale = Ticket.mint(SECRET, new Ticket.Claims(PLAYER, "lobby-1", "game-1", null, "t",
                System.currentTimeMillis() - Ticket.CLOCK_SKEW_MILLIS - 1));

        assertNull(Ticket.verify(SECRET, stale, "game-1", PLAYER));
    }

    @Test
    void editingTheClaimsBreaksTheSignature() {
        final String[] parts = ticketTo("game-1").split("\\.");
        final String edited = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("game-1", "game-2");
        final String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(edited.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertNull(Ticket.verify(SECRET, forged, "game-2", PLAYER));
    }

    @Test
    void garbageIsRefusedRatherThanThrown() {
        assertNull(Ticket.verify(SECRET, null, "game-1", PLAYER));
        assertNull(Ticket.verify(SECRET, "", "game-1", PLAYER));
        assertNull(Ticket.verify(SECRET, "LK1.", "game-1", PLAYER));
        assertNull(Ticket.verify(SECRET, "LK1.@@@.@@@", "game-1", PLAYER));
        assertNull(Ticket.verify(SECRET, "HT1.abc.def", "game-1", PLAYER));
        assertNull(Ticket.verify("", ticketTo("game-1"), "game-1", PLAYER));
    }

    // Hytale disconnects a client whose referral data exceeds 4096 bytes, so an oversized ticket
    // does not degrade, it stops players joining.
    @Test
    void aTicketFitsWellInsideHytalesReferralLimit() {
        final String ticket = Ticket.mint(SECRET, Ticket.claims(PLAYER, "a-fairly-long-source-server-id",
                "a-fairly-long-target-server-id", "world-" + UUID.randomUUID() + UUID.randomUUID()));

        assertTrue(ticket.getBytes(StandardCharsets.UTF_8).length < 1024);
    }

    @Test
    void theReplayGuardAdmitsATicketOnce() {
        final ReplayGuard guard = new ReplayGuard();
        final Ticket.Claims claims = Ticket.verify(SECRET, ticketTo("game-1"), "game-1", PLAYER);

        assertTrue(guard.firstUse(claims));
        assertFalse(guard.firstUse(claims));
    }
}
