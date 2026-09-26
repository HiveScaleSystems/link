package net.beehivesys.link.hytale.command;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import net.beehivesys.link.LinkException;
import net.beehivesys.link.LinkServer;
import net.beehivesys.link.hytale.LinkPlugin;

/** {@code /play <group>}: matchmake into the best server of a group, e.g. {@code /play skywars}. */
public final class PlayCommand extends PlayerTransferCommand {

    public PlayCommand() {
        super("play", "Join a game", "game", "The game to join, e.g. skywars");
    }

    @Override
    LinkServer transfer(final PlayerRef player, final String target) throws LinkException {
        return LinkPlugin.play(player, target);
    }
}
