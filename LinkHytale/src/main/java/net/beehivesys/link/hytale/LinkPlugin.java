package net.beehivesys.link.hytale;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import net.beehivesys.link.Link;
import net.beehivesys.link.LinkConfig;
import net.beehivesys.link.LinkException;
import net.beehivesys.link.LinkLog;
import net.beehivesys.link.LinkPlayer;
import net.beehivesys.link.LinkServer;
import net.beehivesys.link.hytale.command.FindCommand;
import net.beehivesys.link.hytale.command.LinkStatusCommand;
import net.beehivesys.link.hytale.command.PlayCommand;
import net.beehivesys.link.hytale.command.PlayersCommand;
import net.beehivesys.link.hytale.command.ServerCommand;
import net.beehivesys.link.hytale.command.ServersCommand;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Link for Hytale: signed transfers and matchmaking between servers, configured in
 * {@code link.json} in this plugin's data folder.
 *
 * <p>Other plugins use the static methods here: {@link #send(PlayerRef, String)} to move a player to
 * a named server, {@link #play(PlayerRef, String)} to matchmake them into a group, and
 * {@link #servers(String)} to see which servers are out there, and {@link #players()} and
 * {@link #find(String)} to see who is where.
 */
public final class LinkPlugin extends JavaPlugin {

    private static volatile LinkPlugin instance;

    private Link link;
    private ArrivalListener arrivals;

    public LinkPlugin(@Nonnull final JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        final LinkLog log = logTo(getLogger());
        final LinkConfig config;
        try {
            config = LinkConfig.load(getDataDirectory().resolve("link.json"), System.getenv());
        } catch (final Exception e) {
            log.warn("[Link] Could not read link.json, transfers are off: " + e.getMessage());
            return;
        }
        final List<String> problems = config.problems();
        if (!problems.isEmpty()) {
            problems.forEach(problem -> log.warn("[Link] " + problem));
            log.warn("[Link] Transfers are off until link.json is fixed ("
                    + getDataDirectory().resolve("link.json") + ")");
            return;
        }

        link = new Link(config, Link.backendFor(config), log,
                // Link fills in the server id, so it is left null here.
                () -> Universe.get().getPlayers().stream()
                        .map(p -> new LinkPlayer(p.getUuid(), p.getUsername(), null))
                        .toList(),
                HytaleServer.get().getConfig().getMaxPlayers());
        arrivals = new ArrivalListener(link, log);
        arrivals.register(getEventRegistry());

        getCommandRegistry().registerCommand(new ServerCommand());
        getCommandRegistry().registerCommand(new PlayCommand());
        getCommandRegistry().registerCommand(new ServersCommand());
        getCommandRegistry().registerCommand(new PlayersCommand());
        getCommandRegistry().registerCommand(new FindCommand());
        getCommandRegistry().registerCommand(new LinkStatusCommand());
    }

    @Override
    protected void start() {
        if (link == null) {
            return;
        }
        link.start();
        final LinkServer self = link.self();
        getLogger().at(Level.INFO).log("[Link] " + self.id() + " (" + self.group() + ") joined the network via "
                + link.config().backend.name().toLowerCase() + ", " + link.servers().size() + " servers known");
    }

    @Override
    protected void shutdown() {
        if (link != null) {
            link.close();
        }
    }

    /** The running network, or null when Link is not configured. */
    public static Link link() {
        final LinkPlugin plugin = instance;
        return plugin == null ? null : plugin.link;
    }

    /**
     * Every server in the network, this one included. Read from Link's local copy of the registry, so
     * it is cheap enough for a menu. Empty when Link is not running on this server.
     */
    public static List<LinkServer> servers() {
        final Link link = link();
        return link == null ? List.of() : link.servers();
    }

    /** The servers in one group, e.g. {@code lobby}. Empty when there are none or Link is not running. */
    public static List<LinkServer> servers(final String group) {
        final Link link = link();
        return link == null ? List.of() : link.servers(group);
    }

    /**
     * Everyone online in the network. Other servers' players are as of their last heartbeat, a few
     * seconds old at most. Needs a Redis or HTTP registry to include other servers: in a static
     * network, only this server's players are known. Empty when Link is not running.
     */
    public static List<LinkPlayer> players() {
        final Link link = link();
        return link == null ? List.of() : link.players();
    }

    /** The players on one server, by id. Empty when it has none, doesn't share its list, or Link is not running. */
    public static List<LinkPlayer> players(final String serverId) {
        final Link link = link();
        return link == null ? List.of() : link.players(serverId);
    }

    /**
     * Where a player is, by name (not case sensitive) or uuid. Null when they are not online, or are
     * on a server that doesn't share its list.
     */
    public static LinkPlayer find(final String nameOrUuid) {
        final Link link = link();
        return link == null ? null : link.find(nameOrUuid);
    }

    /** Sends a player to one named server. */
    public static LinkServer send(final PlayerRef player, final String serverId) throws LinkException {
        return refer(player, running().routeTo(player.getUuid(), serverId, null));
    }

    /** Sends a player to one named server, landing in a specific world there. */
    public static LinkServer send(final PlayerRef player, final String serverId, final String world)
            throws LinkException {
        return refer(player, running().routeTo(player.getUuid(), serverId, world));
    }

    /** Matchmakes a player into the best server of a group. */
    public static LinkServer play(final PlayerRef player, final String group) throws LinkException {
        return refer(player, running().routeToGroup(player.getUuid(), group));
    }

    private static Link running() throws LinkException {
        final Link link = link();
        if (link == null) {
            throw new LinkException("Transfers are not set up on this server.");
        }
        return link;
    }

    private static LinkServer refer(final PlayerRef player, final Link.Route route) {
        player.referToServer(route.server().host(), route.server().port(), route.ticket());
        return route.server();
    }

    private static LinkLog logTo(final HytaleLogger logger) {
        return new LinkLog() {
            @Override
            public void info(final String message) {
                logger.at(Level.INFO).log(message);
            }

            @Override
            public void warn(final String message) {
                logger.at(Level.WARNING).log(message);
            }
        };
    }
}
