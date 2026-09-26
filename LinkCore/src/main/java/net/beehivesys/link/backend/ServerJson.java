package net.beehivesys.link.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.beehivesys.link.LinkServer;

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
        return json;
    }

    static LinkServer fromJson(final JsonObject json) {
        return new LinkServer(
                json.get("id").getAsString(),
                json.get("group").getAsString(),
                json.get("host").getAsString(),
                json.get("port").getAsInt(),
                json.has("players") ? json.get("players").getAsInt() : -1,
                json.has("maxPlayers") ? json.get("maxPlayers").getAsInt() : -1,
                json.has("lastSeen") ? json.get("lastSeen").getAsLong() : System.currentTimeMillis());
    }

    static LinkServer parse(final String raw) {
        return fromJson(JsonParser.parseString(raw).getAsJsonObject());
    }
}
