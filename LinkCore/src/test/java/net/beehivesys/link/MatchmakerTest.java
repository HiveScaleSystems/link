package net.beehivesys.link;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatchmakerTest {

    private final Matchmaker matchmaker = new Matchmaker();

    private static LinkServer server(final String id, final String group, final int players, final int max) {
        return new LinkServer(id, group, "10.0.0.1", 5520, players, max, 1L);
    }

    @Test
    void fillPicksTheBusiestServerWithRoom() {
        final List<LinkServer> servers = List.of(
                server("sw-1", "skywars", 3, 12),
                server("sw-2", "skywars", 9, 12),
                server("sw-3", "skywars", 12, 12));

        assertEquals("sw-2", matchmaker.pick(servers, "skywars", null, LinkConfig.Strategy.FILL).id());
    }

    @Test
    void spreadPicksTheEmptiestServer() {
        final List<LinkServer> servers = List.of(
                server("lobby-1", "lobby", 40, 100),
                server("lobby-2", "lobby", 10, 100));

        assertEquals("lobby-2", matchmaker.pick(servers, "lobby", null, LinkConfig.Strategy.SPREAD).id());
    }

    @Test
    void theAskingServerIsNeverPicked() {
        final List<LinkServer> servers = List.of(server("lobby-1", "lobby", 1, 100));

        assertNull(matchmaker.pick(servers, "lobby", "lobby-1", LinkConfig.Strategy.SPREAD));
    }

    @Test
    void aGroupThatIsAllFullGivesNothing() {
        final List<LinkServer> servers = List.of(server("sw-1", "skywars", 12, 12));

        assertNull(matchmaker.pick(servers, "skywars", null, LinkConfig.Strategy.FILL));
    }

    @Test
    void groupsMatchCaseInsensitively() {
        final List<LinkServer> servers = List.of(server("sw-1", "SkyWars", 1, 12));

        assertEquals("sw-1", matchmaker.pick(servers, "skywars", null, LinkConfig.Strategy.FILL).id());
    }

    @Test
    void aStaticListWithoutLoadTakesTurns() {
        final List<LinkServer> servers = List.of(
                server("lobby-1", "lobby", -1, -1),
                server("lobby-2", "lobby", -1, -1));
        final Set<String> picked = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            picked.add(matchmaker.pick(servers, "lobby", null, LinkConfig.Strategy.FILL).id());
        }

        assertEquals(Set.of("lobby-1", "lobby-2"), picked);
    }
}
