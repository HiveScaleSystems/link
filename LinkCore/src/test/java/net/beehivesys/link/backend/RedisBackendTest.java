package net.beehivesys.link.backend;

import net.beehivesys.link.LinkConfig;
import net.beehivesys.link.LinkServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Needs a real Redis: {@code LINK_TEST_REDIS_URL=redis://localhost:6379 ./gradlew :LinkCore:test}. */
@EnabledIfEnvironmentVariable(named = "LINK_TEST_REDIS_URL", matches = ".+")
class RedisBackendTest {

    private static RedisBackend backend(final String namespace) {
        final LinkConfig.Redis redis = new LinkConfig.Redis();
        redis.url = System.getenv("LINK_TEST_REDIS_URL");
        redis.namespace = namespace;
        return new RedisBackend(redis);
    }

    @Test
    void twoServersShareOneSecretSeeEachOtherAndForgetLeavers() {
        final String ns = "link-test-" + UUID.randomUUID();
        try (RedisBackend lobby = backend(ns); RedisBackend game = backend(ns)) {
            final String secret = lobby.secret();
            assertFalse(secret.isBlank());
            assertEquals(secret, game.secret(), "the first server's secret is the network's");

            lobby.sync(new LinkServer("lobby-1", "lobby", "10.0.0.1", 5520, 3, 100, 0));
            final List<LinkServer> seen = game.sync(new LinkServer("game-1", "skywars", "10.0.0.2", 5520, 0, 12, 0));
            assertEquals(2, seen.size());
            assertTrue(seen.stream().anyMatch(s -> s.id().equals("lobby-1") && s.players() == 3));

            lobby.leave("lobby-1");
            assertTrue(game.sync(new LinkServer("game-1", "skywars", "10.0.0.2", 5520, 0, 12, 0))
                    .stream().noneMatch(s -> s.id().equals("lobby-1")));
            game.leave("game-1");
        }
    }
}
