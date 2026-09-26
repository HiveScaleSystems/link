package net.beehivesys.link;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code link.json}. Plain Gson rather than an engine codec so the same file and the same parsing
 * work on every engine Link runs on.
 *
 * <p>Environment variables win over the file ({@code LINK_SERVER_ID}, {@code LINK_SECRET}, ...), so a
 * container can be wired entirely from its environment.
 */
public final class LinkConfig {

    public enum Backend {
        @SerializedName(value = "static", alternate = "STATIC") STATIC,
        @SerializedName(value = "redis", alternate = "REDIS") REDIS,
        @SerializedName(value = "http", alternate = "HTTP") HTTP
    }

    public enum Strategy {
        /** Fill the busiest server that still has room: fewer, fuller servers, which suits minigames. */
        @SerializedName(value = "fill", alternate = "FILL") FILL,
        /** Send each player to the emptiest server: even load, which suits lobbies. */
        @SerializedName(value = "spread", alternate = "SPREAD") SPREAD
    }

    public String serverId = "lobby-1";
    public String group = "lobby";
    /** The address OTHER servers refer players to. Must be reachable from a player's client. */
    public String host = "";
    public int port = 5520;

    public Backend backend = Backend.STATIC;
    /**
     * Signs every ticket in the network, so it must be identical on every server. Static networks
     * generate one on first boot; registry networks keep it in the registry and leave this empty.
     */
    public String secret = "";

    /** The whole network, for {@link Backend#STATIC}. The same list goes on every server. */
    public List<StaticServer> servers = new ArrayList<>(List.of(
            new StaticServer("lobby-1", "lobby", "127.0.0.1", 5520)));

    public Redis redis = new Redis();
    public Http http = new Http();

    /**
     * Refuse players who connect without a valid ticket and send them to {@link #fallbackGroup}.
     * Turn on for game servers that should only be reachable through the lobby.
     */
    public boolean requireTicket = false;
    public String fallbackGroup = "lobby";
    public Strategy strategy = Strategy.FILL;

    public static final class StaticServer {
        public String id;
        public String group;
        public String host;
        public int port = 5520;

        public StaticServer() {
        }

        public StaticServer(final String id, final String group, final String host, final int port) {
            this.id = id;
            this.group = group;
            this.host = host;
            this.port = port;
        }
    }

    public static final class Redis {
        /** {@code redis://:password@host:6379/0}, or {@code rediss://} for TLS. */
        public String url = "";
        /** Key prefix, so several networks can share one Redis. */
        public String namespace = "link";
    }

    public static final class Http {
        /** A registry speaking the Link HTTP protocol, e.g. the Cloudflare Worker template. */
        public String url = "";
        public String token = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Reads the file, creating it with defaults on first boot, and writes back any fields a newer
     * version added. A static network with no secret yet gets one generated and saved here, so the
     * operator copies one finished file to every server.
     */
    public static LinkConfig load(final Path file, final Map<String, String> env) throws IOException {
        LinkConfig config = null;
        if (Files.exists(file)) {
            config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LinkConfig.class);
        }
        if (config == null) {
            config = new LinkConfig();
        }
        if (config.backend == Backend.STATIC && (config.secret == null || config.secret.isBlank())) {
            config.secret = generateSecret();
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);

        config.applyEnvironment(env::get);
        return config;
    }

    void applyEnvironment(final Function<String, String> env) {
        serverId = env(env, "LINK_SERVER_ID", serverId);
        group = env(env, "LINK_GROUP", group);
        host = env(env, "LINK_HOST", host);
        final String portValue = env.apply("LINK_PORT");
        if (portValue != null && !portValue.isBlank()) {
            port = Integer.parseInt(portValue.trim());
        }
        secret = env(env, "LINK_SECRET", secret);
        redis.url = env(env, "LINK_REDIS_URL", redis.url);
        http.url = env(env, "LINK_HTTP_URL", http.url);
        http.token = env(env, "LINK_HTTP_TOKEN", http.token);
    }

    private static String env(final Function<String, String> env, final String name, final String fallback) {
        final String value = env.apply(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Everything that would stop Link from working, worded for the operator reading the log. */
    public List<String> problems() {
        final List<String> problems = new ArrayList<>();
        if (serverId == null || serverId.isBlank()) {
            problems.add("serverId is empty");
        }
        if (group == null || group.isBlank()) {
            problems.add("group is empty");
        }
        switch (backend) {
            case STATIC -> {
                if (servers == null || servers.stream().noneMatch(s -> serverId.equals(s.id))) {
                    problems.add("this server (" + serverId + ") is not in the servers list;"
                            + " add it so every server shares the same list");
                }
            }
            case REDIS -> {
                if (redis.url == null || redis.url.isBlank()) {
                    problems.add("backend is REDIS but redis.url is empty");
                }
            }
            case HTTP -> {
                if (http.url == null || http.url.isBlank() || http.token == null || http.token.isBlank()) {
                    problems.add("backend is HTTP but http.url or http.token is empty");
                }
            }
        }
        if (backend != Backend.STATIC && (host == null || host.isBlank())) {
            problems.add("host is empty; other servers need it to send players here");
        }
        return problems;
    }

    public static String generateSecret() {
        final byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
