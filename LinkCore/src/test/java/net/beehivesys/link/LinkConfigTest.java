package net.beehivesys.link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkConfigTest {

    @TempDir
    Path dir;

    @Test
    void firstBootWritesAStaticConfigWithAGeneratedSecret() throws Exception {
        final Path file = dir.resolve("link.json");
        final LinkConfig config = LinkConfig.load(file, Map.of());

        assertFalse(config.secret.isBlank());
        assertTrue(Files.readString(file).contains(config.secret));
        assertTrue(Files.readString(file).contains("\"backend\": \"static\""));
        assertTrue(config.problems().isEmpty(), config.problems().toString());
    }

    @Test
    void theSecretSurvivesARestart() throws Exception {
        final Path file = dir.resolve("link.json");
        final String first = LinkConfig.load(file, Map.of()).secret;

        assertEquals(first, LinkConfig.load(file, Map.of()).secret);
    }

    @Test
    void theEnvironmentWinsButIsNeverWrittenToTheFile() throws Exception {
        final Path file = dir.resolve("link.json");
        final LinkConfig config = LinkConfig.load(file,
                Map.of("LINK_SERVER_ID", "game-7", "LINK_SECRET", "from-env", "LINK_PORT", "5530"));

        assertEquals("game-7", config.serverId);
        assertEquals("from-env", config.secret);
        assertEquals(5530, config.port);
        assertFalse(Files.readString(file).contains("from-env"));
    }

    @Test
    void enumsReadInEitherCase() throws Exception {
        final Path file = dir.resolve("link.json");
        Files.writeString(file, "{\"backend\": \"REDIS\", \"strategy\": \"spread\"}");
        final LinkConfig config = LinkConfig.load(file, Map.of());

        assertEquals(LinkConfig.Backend.REDIS, config.backend);
        assertEquals(LinkConfig.Strategy.SPREAD, config.strategy);
    }

    @Test
    void aRegistryConfigWithoutConnectionDetailsSaysWhatIsMissing() throws Exception {
        final Path file = dir.resolve("link.json");
        Files.writeString(file, "{\"backend\": \"http\"}");

        final String problems = String.join("; ", LinkConfig.load(file, Map.of()).problems());
        assertTrue(problems.contains("http.url"), problems);
        assertTrue(problems.contains("host is empty"), problems);
    }
}
