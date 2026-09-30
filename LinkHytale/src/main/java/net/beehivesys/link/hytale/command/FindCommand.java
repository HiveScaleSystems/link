package net.beehivesys.link.hytale.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import net.beehivesys.link.Link;
import net.beehivesys.link.LinkPlayer;
import net.beehivesys.link.LinkServer;
import net.beehivesys.link.hytale.LinkPlugin;

import javax.annotation.Nonnull;

/**
 * {@code /find <player>}: which server a player is on. Open to everyone, since {@code /players}
 * shows the same thing.
 */
public final class FindCommand extends CommandBase {

    private final RequiredArg<String> player;

    public FindCommand() {
        super("find", "Find which server a player is on");
        player = withRequiredArg("player", "The player's name", ArgTypes.STRING);
        requireNoPermission();
    }

    @Override
    protected void executeSync(@Nonnull final CommandContext ctx) {
        final Link link = LinkPlugin.link();
        final String name = player.get(ctx);
        final LinkPlayer found = link == null ? null : link.find(name);
        if (found == null) {
            final boolean blind = link != null && link.servers().stream().anyMatch(s -> !s.reportsLoad());
            ctx.sendMessage(Message.raw(blind
                    ? "Can't find " + name + ". Some servers don't share who is online, which needs Redis or Cloudflare."
                    : name + " is not online."));
            return;
        }
        if (found.server().equals(link.self().id())) {
            ctx.sendMessage(Message.raw(found.name() + " is on this server."));
            return;
        }
        final LinkServer server = link.server(found.server());
        ctx.sendMessage(Message.raw(found.name() + " is on " + found.server()
                + (server == null ? "" : " (" + server.group() + ")") + ". Type /server " + found.server() + " to join."));
    }
}
