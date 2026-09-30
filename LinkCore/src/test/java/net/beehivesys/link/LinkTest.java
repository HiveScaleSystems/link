package net.beehivesys.link;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.beehivesys.link.backend.HttpBackend;
import net.beehivesys.link.backend.StaticBackend;
import net.beehivesys.link.ticket.Ticket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two servers, one player, the whole hop: route on the source, admit on the target. */
class LinkTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private HttpServer registry;

    @AfterEach
    void stopRegistry() {
        if (registry != null) {
            registry.stop(0);
        }
    }

    private static LinkConfig staticConfig(final String self) {
        final LinkConfig config = new LinkConfig();
        config.serverId = self;
        config.secret = "shared";
        config.servers = List.of(
                new LinkConfig.StaticServer("lobby-1", "lobby", "10.0.0.1", 5520),
                new LinkConfig.StaticServer("game-1", "skywars", "10.0.0.2", 5520));
        return config;
    }

    private static Link staticLink(final String self) {
        return staticLink(self, List.of());
    }

    private static Link staticLink(final String self, final List<LinkPlayer> online) {
        final LinkConfig config = staticConfig(self);
        final Link link = new Link(config, new StaticBackend(config), LinkLog.NONE, () -> online, 100);
        link.start();
        return link;
    }

    @Test
    void aStaticNetworkHopsAPlayerAndTheTicketWorksOnce() throws Exception {
        try (Link lobby = staticLink("lobby-1"); Link game = staticLink("game-1")) {
            final Link.Route route = lobby.routeToGroup(PLAYER, "skywars");

            assertEquals("game-1", route.server().id());
            assertEquals("10.0.0.2", route.server().host());
            final Ticket.Claims claims = game.admit(route.ticket(), PLAYER);
            assertNotNull(claims);
            assertEquals("lobby-1", claims.source());
            assertNull(game.admit(route.ticket(), PLAYER), "a ticket is good for one arrival");
        }
    }

    @Test
    void aTicketIsOnlyGoodAtTheServerItNames() throws Exception {
        try (Link lobby = staticLink("lobby-1"); Link game = staticLink("game-1")) {
            final Link.Route toGame = game.routeTo(PLAYER, "lobby-1", null);

            assertNull(game.admit(toGame.ticket(), PLAYER));
            assertNotNull(lobby.admit(toGame.ticket(), PLAYER));
        }
    }

    @Test
    void asksThatCannotBeRoutedExplainThemselves() {
        try (Link lobby = staticLink("lobby-1")) {
            assertEquals("There is no server called nowhere.",
                    assertThrows(LinkException.class, () -> lobby.routeTo(PLAYER, "nowhere", null)).getMessage());
            assertEquals("You are already on lobby-1.",
                    assertThrows(LinkException.class, () -> lobby.routeTo(PLAYER, "lobby-1", null)).getMessage());
            assertEquals("There are no bedwars servers.",
                    assertThrows(LinkException.class, () -> lobby.routeToGroup(PLAYER, "bedwars")).getMessage());
        }
    }

    @Test
    void aDirectConnectionIsNotATransfer() {
        try (Link game = staticLink("game-1")) {
            assertNull(game.admit(null, PLAYER));
            assertNull(game.admit(new byte[0], PLAYER));
            assertNull(game.admit("not a ticket".getBytes(StandardCharsets.UTF_8), PLAYER));
        }
    }

    @Test
    void anHttpRegistrySharesItsSecretAndServerListAndForgetsLeavers() throws Exception {
        startRegistry("token");
        final Link lobby = httpLink("lobby-1", "lobby", "token");
        final Link game = httpLink("game-1", "skywars", "token");
        try {
            // The first server to register only sees the second on its next heartbeat.
            waitFor(() -> lobby.server("game-1") != null);

            final Link.Route route = lobby.routeToGroup(PLAYER, "skywars");
            assertEquals("game-1", route.server().id());
            assertNotNull(game.admit(route.ticket(), PLAYER), "both servers got the same secret from the registry");

            game.close();
            final List<LinkServer> left = new HttpBackend(http("token"))
                    .sync(new LinkServer("probe", "x", "h", 1, 0, 1, 0));
            assertTrue(left.stream().noneMatch(s -> s.id().equals("game-1")), "a server that left is gone");
        } finally {
            lobby.close();
            game.close();
        }
    }

    @Test
    void aWrongTokenLeavesTransfersUnavailableInsteadOfFailingBoot() throws Exception {
        startRegistry("token");
        try (Link lobby = httpLink("lobby-1", "lobby", "wrong")) {
            assertTrue(lobby.isRegistryDown());
            assertFalse(lobby.isReady());
        }
    }

    @Test
    void anHttpRegistrySharesWhoIsOnlineAndFindsPlayersOnAnyServer() throws Exception {
        startRegistry("token");
        final UUID alex = UUID.randomUUID();
        final List<LinkPlayer> onGame = new CopyOnWriteArrayList<>(List.of(online(alex, "Alex")));
        final Link lobby = httpLink("lobby-1", "lobby", "token", () -> List.of(online(PLAYER, "Steve")));
        final Link game = httpLink("game-1", "skywars", "token", () -> onGame);
        try {
            waitFor(() -> lobby.find("Alex") != null);

            assertEquals("game-1", lobby.find("alex").server(), "names are not case sensitive");
            assertEquals("game-1", lobby.find(alex.toString()).server(), "a uuid works too");
            assertEquals("lobby-1", lobby.find("Steve").server());
            assertEquals(2, lobby.players().size());
            assertEquals(List.of("Alex"), lobby.players("game-1").stream().map(LinkPlayer::name).toList());
            assertNull(lobby.find("Nobody"));

            onGame.clear();
            waitFor(() -> lobby.find("Alex") == null);
            assertTrue(lobby.players("game-1").isEmpty(), "a player who left drops out on the next heartbeat");
        } finally {
            lobby.close();
            game.close();
        }
    }

    @Test
    void aPlayerMidHopIsListedOnceOnTheServerTheyAreLiveOn() throws Exception {
        startRegistry("token");
        // The game server still reports Steve from before he hopped; the lobby has him live.
        final Link game = httpLink("game-1", "skywars", "token", () -> List.of(online(PLAYER, "Steve")));
        final Link lobby = httpLink("lobby-1", "lobby", "token", () -> List.of(online(PLAYER, "Steve")));
        try {
            waitFor(() -> lobby.server("game-1") != null && !lobby.server("game-1").online().isEmpty());

            assertEquals(1, lobby.players().size());
            assertEquals("lobby-1", lobby.find("Steve").server());
        } finally {
            lobby.close();
            game.close();
        }
    }

    @Test
    void aStaticNetworkOnlyKnowsItsOwnPlayers() {
        try (Link lobby = staticLink("lobby-1", List.of(online(PLAYER, "Steve")))) {
            assertEquals("lobby-1", lobby.find("Steve").server());
            assertTrue(lobby.players("game-1").isEmpty());
            assertFalse(lobby.server("game-1").reportsLoad(), "so callers can tell unknown from empty");
        }
    }

    /** A player as the engine reports them: Link fills in the server. */
    private static LinkPlayer online(final UUID uuid, final String name) {
        return new LinkPlayer(uuid, name, null);
    }

    private Link httpLink(final String id, final String group, final String token) {
        return httpLink(id, group, token, List::of);
    }

    private Link httpLink(final String id, final String group, final String token,
                          final java.util.function.Supplier<List<LinkPlayer>> online) {
        final LinkConfig config = new LinkConfig();
        config.serverId = id;
        config.group = group;
        config.host = "10.0.0." + (id.hashCode() & 0xff);
        config.backend = LinkConfig.Backend.HTTP;
        config.http = http(token);
        final Link link = new Link(config, new HttpBackend(config.http), LinkLog.NONE, online, 100);
        link.start();
        return link;
    }

    private LinkConfig.Http http(final String token) {
        final LinkConfig.Http http = new LinkConfig.Http();
        http.url = "http://127.0.0.1:" + registry.getAddress().getPort();
        http.token = token;
        return http;
    }

    /** The Link HTTP registry protocol, in memory. The Cloudflare Worker implements the same thing. */
    private void startRegistry(final String token) throws IOException {
        final Map<String, JsonObject> servers = new ConcurrentHashMap<>();
        final String secret = LinkConfig.generateSecret();
        registry = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        registry.createContext("/v1/", exchange -> {
            if (!("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                reply(exchange, 401, "{}");
                return;
            }
            final String path = exchange.getRequestURI().getPath();
            if (path.equals("/v1/secret")) {
                final JsonObject body = new JsonObject();
                body.addProperty("secret", secret);
                reply(exchange, 200, body.toString());
                return;
            }
            final String id = path.substring("/v1/servers/".length());
            if (exchange.getRequestMethod().equals("DELETE")) {
                servers.remove(id);
                reply(exchange, 204, "");
                return;
            }
            final JsonObject server = JsonParser.parseString(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            server.addProperty("lastSeen", System.currentTimeMillis());
            servers.put(id, server);
            final JsonArray list = new JsonArray();
            servers.values().forEach(list::add);
            final JsonObject body = new JsonObject();
            body.add("servers", list);
            reply(exchange, 200, body.toString());
        });
        registry.start();
    }

    private static void reply(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static void waitFor(final java.util.function.BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + Link.HTTP_SYNC_INTERVAL_MILLIS * 2;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met in time");
            }
            Thread.sleep(50);
        }
    }
}
