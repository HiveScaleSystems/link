package net.beehivesys.link.hytale.command;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import net.beehivesys.link.LinkException;
import net.beehivesys.link.LinkServer;
import net.beehivesys.link.hytale.LinkPlugin;

/** {@code /server <name>}: go to one specific server. */
public final class ServerCommand extends PlayerTransferCommand {

    public ServerCommand() {
        super("server", "Go to another server", "name", "The server to go to");
    }

    @Override
    LinkServer transfer(final PlayerRef player, final String target) throws LinkException {
        return LinkPlugin.send(player, target);
    }
}
