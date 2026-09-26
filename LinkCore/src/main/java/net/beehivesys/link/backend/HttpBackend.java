package net.beehivesys.link.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.beehivesys.link.LinkConfig;
import net.beehivesys.link.LinkServer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A registry over HTTPS, such as the Cloudflare Worker template in {@code cloudflare-registry/}.
 * The protocol is small enough to host anywhere:
 *
 * <pre>
 *   GET    /v1/secret        -> {"secret": "..."}      created on first call
 *   PUT    /v1/servers/{id}  body: server JSON  -> {"servers": [...]}   heartbeat + live list
 *   DELETE /v1/servers/{id}  -> 204
 * </pre>
 *
 * Every request carries {@code Authorization: Bearer <token>}. The registry stamps
 * {@code lastSeen} itself and drops servers that stop sending heartbeats.
 */
public final class HttpBackend implements NetworkBackend {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String base;
    private final String token;

    public HttpBackend(final LinkConfig.Http config) {
        this.base = config.url.endsWith("/") ? config.url.substring(0, config.url.length() - 1) : config.url;
        this.token = config.token;
    }

    @Override
    public String secret() throws IOException, InterruptedException {
        return send(request("/v1/secret").GET()).getAsJsonObject().get("secret").getAsString();
    }

    @Override
    public List<LinkServer> sync(final LinkServer self) throws IOException, InterruptedException {
        final JsonElement body = send(request("/v1/servers/" + encode(self.id()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(ServerJson.toJson(self).toString())));
        final List<LinkServer> servers = new ArrayList<>();
        for (final JsonElement element : body.getAsJsonObject().getAsJsonArray("servers")) {
            try {
                servers.add(ServerJson.fromJson(element.getAsJsonObject()));
            } catch (final RuntimeException ignored) {
                // Skip a malformed entry rather than losing the whole list.
            }
        }
        return servers;
    }

    @Override
    public void leave(final String serverId) throws IOException, InterruptedException {
        send(request("/v1/servers/" + encode(serverId)).DELETE());
    }

    @Override
    public void close() {
        client.close();
    }

    private HttpRequest.Builder request(final String path) {
        return HttpRequest.newBuilder(URI.create(base + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token);
    }

    private JsonElement send(final HttpRequest.Builder request) throws IOException, InterruptedException {
        final HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("the registry refused the token (HTTP " + response.statusCode() + ")");
        }
        if (response.statusCode() >= 300) {
            throw new IOException("the registry answered HTTP " + response.statusCode());
        }
        return response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body());
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
