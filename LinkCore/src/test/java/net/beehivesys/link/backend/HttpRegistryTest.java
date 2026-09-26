package net.beehivesys.link.backend;

import net.beehivesys.link.Link;
import net.beehivesys.link.LinkConfig;
import net.beehivesys.link.LinkLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Against a real registry, e.g. the Cloudflare Worker under {@code wrangler dev}:
 * {@code LINK_TEST_HTTP_URL=http://127.0.0.1:8799 LINK_TEST_HTTP_TOKEN=dev-token ./gradlew :LinkCore:test}.
 */
@EnabledIfEnvironmentVariable(named = "LINK_TEST_HTTP_URL", matches = ".+")
class HttpRegistryTest {

    private static Link join(final String id, final String group) {
        final LinkConfig config = new LinkConfig();
        config.serverId = id;
        config.group = group;
        config.host = "10.0.0.1";
        config.backend = LinkConfig.Backend.HTTP;
        config.http.url = System.getenv("LINK_TEST_HTTP_URL");
        config.http.token = System.getenv("LINK_TEST_HTTP_TOKEN");
        final Link link = new Link(config, new HttpBackend(config.http), LinkLog.NONE, () -> 0, 12);
        link.start();
        return link;
    }

    @Test
    void aPlayerHopsBetweenTwoServersThroughTheRegistry() throws Exception {
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        final UUID player = UUID.randomUUID();
        try (Link game = join("game-" + suffix, "test-" + suffix); Link lobby = join("lobby-" + suffix, "lobby")) {
            // The lobby registered second, so the game server is already in its first list.
            final Link.Route route = lobby.routeToGroup(player, "test-" + suffix);

            assertEquals("game-" + suffix, route.server().id());
            assertNotNull(game.admit(route.ticket(), player));
        }
    }
}
