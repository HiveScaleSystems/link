package net.beehivesys.link;

import java.util.UUID;

/**
 * A player online somewhere in the network, as the last heartbeat of their server reported it.
 *
 * @param server the id of the server they are on
 */
public record LinkPlayer(UUID uuid, String name, String server) {
}
