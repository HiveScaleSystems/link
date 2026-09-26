package net.beehivesys.link.hytale.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import net.beehivesys.link.Link;
import net.beehivesys.link.LinkServer;
import net.beehivesys.link.hytale.LinkPlugin;

import javax.annotation.Nonnull;

/**
 * {@code /link}: whether this server is connected, for the operator. Keeps Hytale's generated
 * permission, so only admins see it.
 */
public final class LinkStatusCommand extends CommandBase {

    public LinkStatusCommand() {
        super("link", "Show Link's connection status");
    }

    @Override
    protected void executeSync(@Nonnull final CommandContext ctx) {
        final Link link = LinkPlugin.link();
        if (link == null) {
            ctx.sendMessage(Message.raw("Link is not running. Check the server log for what link.json is missing."));
            return;
        }
        final LinkServer self = link.self();
        ctx.sendMessage(Message.raw("This server: " + self.id() + " in group " + self.group()
                + " at " + self.host() + ":" + self.port()));
        ctx.sendMessage(Message.raw("Registry: " + link.config().backend.name().toLowerCase()
                + (link.isRegistryDown() ? " (unreachable, using the last known list)" : " (ok)")));
        ctx.sendMessage(Message.raw("Transfers: " + (link.isReady() ? "ready" : "waiting for the network secret")
                + ", " + link.servers().size() + " servers known"));
    }
}
