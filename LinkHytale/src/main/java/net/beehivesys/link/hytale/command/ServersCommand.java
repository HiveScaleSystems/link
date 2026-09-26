package net.beehivesys.link.hytale.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import net.beehivesys.link.Link;
import net.beehivesys.link.LinkServer;
import net.beehivesys.link.hytale.LinkPlugin;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** {@code /servers}: every server in the network, by group. */
public final class ServersCommand extends CommandBase {

    public ServersCommand() {
        super("servers", "List the servers in this network");
        requireNoPermission();
    }

    @Override
    protected void executeSync(@Nonnull final CommandContext ctx) {
        final Link link = LinkPlugin.link();
        if (link == null || link.servers().isEmpty()) {
            ctx.sendMessage(Message.raw("No servers are known yet."));
            return;
        }
        final String here = link.self().id();
        final Map<String, List<LinkServer>> byGroup = new TreeMap<>(link.servers().stream()
                .collect(Collectors.groupingBy(LinkServer::group)));
        byGroup.forEach((group, servers) -> ctx.sendMessage(Message.raw(group + ": " + servers.stream()
                .sorted((a, b) -> a.id().compareToIgnoreCase(b.id()))
                .map(s -> s.id() + (s.reportsLoad() ? " (" + s.players() + "/" + s.maxPlayers() + ")" : "")
                        + (s.id().equals(here) ? " <- you" : ""))
                .collect(Collectors.joining(", ")))));
    }
}
