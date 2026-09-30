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
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /players}: who is online in the whole network, per server. {@code /players <server>} lists
 * one server. Hytale's own {@code /who} still lists this server only.
 */
public final class PlayersCommand extends CommandBase {

    public PlayersCommand() {
        super("players", "List the players in this network");
        requireNoPermission();
        addUsageVariant(new OnServer());
    }

    @Override
    protected void executeSync(@Nonnull final CommandContext ctx) {
        final Link link = LinkPlugin.link();
        if (link == null) {
            ctx.sendMessage(Message.raw("Link is not running on this server."));
            return;
        }
        final List<LinkPlayer> everyone = link.players();
        ctx.sendMessage(Message.raw(everyone.size() + (everyone.size() == 1 ? " player" : " players")
                + " online in the network"));
        // Only servers with someone on them. link.players(id) is live for this server.
        link.servers().stream()
                .filter(LinkServer::reportsLoad)
                .map(LinkServer::id)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(id -> {
                    final List<LinkPlayer> on = link.players(id);
                    if (!on.isEmpty()) {
                        ctx.sendMessage(Message.raw(line(id, on)));
                    }
                });
        if (link.servers().stream().anyMatch(s -> !s.reportsLoad())) {
            ctx.sendMessage(Message.raw("Some servers don't share who is online. That needs Redis or Cloudflare.")
                    .color("#aaaaaa"));
        }
    }

    /** {@code /players <server>}. Hytale picks this variant when the command gets one argument. */
    private static final class OnServer extends CommandBase {

        private final RequiredArg<String> server;

        OnServer() {
            super("List the players on one server");
            server = withRequiredArg("server", "The server to list, e.g. lobby-1", ArgTypes.STRING);
            requireNoPermission();
        }

        @Override
        protected void executeSync(@Nonnull final CommandContext ctx) {
            final Link link = LinkPlugin.link();
            final String id = server.get(ctx);
            final LinkServer target = link == null ? null : link.server(id);
            if (target == null) {
                ctx.sendMessage(Message.raw("There is no server called " + id + "."));
                return;
            }
            if (!target.reportsLoad()) {
                ctx.sendMessage(Message.raw(target.id() + " doesn't share who is online. That needs Redis or Cloudflare."));
                return;
            }
            ctx.sendMessage(Message.raw(line(target.id(), link.players(target.id()))));
        }
    }

    private static String line(final String server, final List<LinkPlayer> players) {
        if (players.isEmpty()) {
            return server + " (0)";
        }
        return server + " (" + players.size() + "): " + players.stream()
                .map(LinkPlayer::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }
}
