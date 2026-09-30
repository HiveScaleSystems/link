package net.beehivesys.link.hytale.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import net.beehivesys.link.Link;
import net.beehivesys.link.LinkServer;
import net.beehivesys.link.hytale.LinkPlugin;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** {@code /servers}: every server in the network, by group. {@code /servers <group>} lists one group. */
public final class ServersCommand extends CommandBase {

    public ServersCommand() {
        super("servers", "List the servers in this network");
        requireNoPermission();
        addUsageVariant(new InGroup());
    }

    @Override
    protected void executeSync(@Nonnull final CommandContext ctx) {
        list(ctx, null);
    }

    /** {@code /servers <group>}. Hytale picks this variant when the command gets one argument. */
    private static final class InGroup extends CommandBase {

        private final RequiredArg<String> group;

        InGroup() {
            super("List the servers in one group");
            group = withRequiredArg("group", "The group to list, e.g. lobby", ArgTypes.STRING);
            requireNoPermission();
        }

        @Override
        protected void executeSync(@Nonnull final CommandContext ctx) {
            list(ctx, group.get(ctx));
        }
    }

    /** @param group only this group, or null for all of them */
    private static void list(final CommandContext ctx, final String group) {
        final Link link = LinkPlugin.link();
        final List<LinkServer> servers = link == null ? List.of()
                : group == null ? link.servers() : link.servers(group);
        if (servers.isEmpty()) {
            ctx.sendMessage(Message.raw(group == null ? "No servers are known yet." : "There are no " + group + " servers."));
            return;
        }
        final String here = link.self().id();
        final Map<String, List<LinkServer>> byGroup = new TreeMap<>(servers.stream()
                .collect(Collectors.groupingBy(LinkServer::group)));
        byGroup.forEach((name, members) -> ctx.sendMessage(Message.raw(name + ": " + members.stream()
                .sorted((a, b) -> a.id().compareToIgnoreCase(b.id()))
                .map(s -> s.id() + (s.reportsLoad() ? " (" + s.players() + "/" + s.maxPlayers() + ")" : "")
                        + (s.id().equals(here) ? " <- you" : ""))
                .collect(Collectors.joining(", ")))));
    }
}
