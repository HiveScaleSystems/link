package net.beehivesys.link.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.beehivesys.link.LinkPlayer;
import net.beehivesys.link.LinkServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The wire form of a {@link LinkServer}, shared by the Redis and HTTP registries. Built by hand
 * rather than by Gson reflection because the game server supplies Gson, and older versions cannot
 * bind records.
 */
final class ServerJson {

    private ServerJson() {
    }

    static JsonObject toJson(final LinkServer server) {
        final JsonObject json = new JsonObject();
        json.addProperty("id", server.id());
        json.addProperty("group", server.group());
        json.addProperty("host", server.host());
        json.addProperty("port", server.port());
        json.addProperty("players", server.players());
        json.addProperty("maxPlayers", server.maxPlayers());
        final JsonArray online = new JsonArray();
        for (final LinkPlayer player : server.online()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("uuid", player.uuid().toString());
            entry.addProperty("name", player.name());
            online.add(entry);
        }
        json.add("online", online);
        return json;
    }

    static LinkServer fromJson(final JsonObject json) {
        final String id = json.get("id").getAsString();
        return new LinkServer(
                id,
                json.get("group").getAsString(),
                json.get("host").getAsString(),
                json.get("port").getAsInt(),
                json.has("players") ? json.get("players").getAsInt() : -1,
                json.has("maxPlayers") ? json.get("maxPlayers").getAsInt() : -1,
                json.has("lastSeen") ? json.get("lastSeen").getAsLong() : System.currentTimeMillis(),
                online(json, id));
    }

    /** The server's players, skipping any entry that doesn't parse. Missing on older versions. */
    private static List<LinkPlayer> online(final JsonObject json, final String server) {
        if (!json.has("online") || !json.get("online").isJsonArray()) {
            return List.of();
        }
        final List<LinkPlayer> players = new ArrayList<>();
        for (final JsonElement element : json.getAsJsonArray("online")) {
            try {
                final JsonObject entry = element.getAsJsonObject();
                players.add(new LinkPlayer(UUID.fromString(entry.get("uuid").getAsString()),
                        entry.get("name").getAsString(), server));
            } catch (final RuntimeException ignored) {
                // One bad entry must not hide the rest of the list.
            }
        }
        return players;
    }

    static LinkServer parse(final String raw) {
        return fromJson(JsonParser.parseString(raw).getAsJsonObject());
    }
}
