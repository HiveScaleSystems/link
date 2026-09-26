package net.beehivesys.link.hytale.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import net.beehivesys.link.LinkException;
import net.beehivesys.link.LinkServer;

import javax.annotation.Nonnull;

/** A command a player runs to move themselves, taking one name: a server or a group. */
abstract class PlayerTransferCommand extends CommandBase {

    private final RequiredArg<String> target;

    PlayerTransferCommand(final String name, final String description, final String argName, final String argDescription) {
        super(name, description);
        target = withRequiredArg(argName, argDescription, ArgTypes.STRING);
        // Moving yourself between servers is for everyone, not just admins.
        requireNoPermission();
    }

    abstract LinkServer transfer(PlayerRef player, String target) throws LinkException;

    @Override
    protected void executeSync(@Nonnull final CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("Only players can be transferred."));
            return;
        }
        final PlayerRef player = Universe.get().getPlayer(ctx.sender().getUuid());
        if (player == null) {
            return;
        }
        try {
            final LinkServer server = transfer(player, target.get(ctx));
            ctx.sendMessage(Message.raw("Sending you to " + server.id() + "..."));
        } catch (final LinkException e) {
            ctx.sendMessage(Message.raw(e.getMessage()).color("#ff5555"));
        }
    }
}
