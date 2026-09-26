package net.beehivesys.link.ticket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * The permit a player carries through a referral: "the network sent player P from S to D".
 *
 * <p>Hytale's referral data rides the client, so the destination trusts none of it until the
 * signature checks out. Signing uses the network secret every server shares, which means any
 * server in the network can admit a player to any other. That is the standalone trust model on
 * purpose: one operator runs every server. A network that needs a compromised server to be unable
 * to forge arrivals elsewhere needs a central signer holding per-server keys.
 *
 * <p>Format: {@code LK1.<base64url json claims>.<base64url HMAC-SHA256>}. The version tag sits
 * outside the payload so a future format fails to parse instead of half-verifying.
 */
public final class Ticket {

    private static final String PREFIX = "LK1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** Long enough for the client to reconnect, short enough to bound a replay. */
    public static final long TTL_MILLIS = 30_000L;

    /** Tolerated clock drift between two servers, since the source stamps expiry and the target checks it. */
    public static final long CLOCK_SKEW_MILLIS = 10_000L;

    /**
     * @param world the world to land in on the destination, or null for its default
     * @param id    unique per ticket, so the destination can refuse a second use
     */
    public record Claims(UUID player, String source, String target, String world, String id, long expiresAt) {

        public boolean hasWorld() {
            return world != null && !world.isEmpty();
        }
    }

    private Ticket() {
    }

    public static Claims claims(final UUID player, final String source, final String target, final String world) {
        return new Claims(player, source, target, world, UUID.randomUUID().toString(),
                System.currentTimeMillis() + TTL_MILLIS);
    }

    public static String mint(final String secret, final Claims claims) {
        final JsonObject body = new JsonObject();
        body.addProperty("p", claims.player().toString());
        body.addProperty("s", claims.source());
        body.addProperty("d", claims.target());
        if (claims.hasWorld()) {
            body.addProperty("w", claims.world());
        }
        body.addProperty("i", claims.id());
        body.addProperty("e", claims.expiresAt());

        final String signed = PREFIX + "." + ENCODER.encodeToString(body.toString().getBytes(StandardCharsets.UTF_8));
        return signed + "." + ENCODER.encodeToString(mac(secret, signed));
    }

    /**
     * @param expectedTarget this server's id; a ticket for another server is refused even though
     *                       every server shares the secret, which is what stops a ticket for a
     *                       quiet server being replayed at a busy one
     * @param expectedPlayer the uuid of the connecting player; a ticket admits exactly one player
     * @return the claims, or null for anything malformed, forged, expired or addressed elsewhere
     */
    public static Claims verify(final String secret, final String ticket,
                                final String expectedTarget, final UUID expectedPlayer) {
        if (secret == null || secret.isEmpty() || ticket == null || !ticket.startsWith(PREFIX + ".")) {
            return null;
        }
        final int lastDot = ticket.lastIndexOf('.');
        if (lastDot <= PREFIX.length()) {
            return null;
        }
        final String signed = ticket.substring(0, lastDot);
        final JsonObject body;
        try {
            final byte[] presented = DECODER.decode(ticket.substring(lastDot + 1));
            if (!MessageDigest.isEqual(presented, mac(secret, signed))) {
                return null;
            }
            body = JsonParser.parseString(new String(
                    DECODER.decode(signed.substring(PREFIX.length() + 1)), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (final RuntimeException e) {
            return null;
        }

        try {
            final long expiresAt = body.get("e").getAsLong();
            if (expiresAt + CLOCK_SKEW_MILLIS <= System.currentTimeMillis()) {
                return null;
            }
            final String target = body.get("d").getAsString();
            final UUID player = UUID.fromString(body.get("p").getAsString());
            if (!target.equals(expectedTarget) || !player.equals(expectedPlayer)) {
                return null;
            }
            return new Claims(player, body.get("s").getAsString(), target,
                    body.has("w") ? body.get("w").getAsString() : null,
                    body.get("i").getAsString(), expiresAt);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static byte[] mac(final String secret, final String signed) {
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw new IllegalStateException("could not sign a transfer ticket", e);
        }
    }
}
