package net.beehivesys.link.backend;

import net.beehivesys.link.LinkServer;

import java.util.List;

/**
 * Where a server learns who else is in the network. Every call is blocking and runs on Link's own
 * scheduler thread, never on the game thread.
 */
public interface NetworkBackend extends AutoCloseable {

    /** The secret every ticket in the network is signed with. Read once at startup. */
    String secret() throws Exception;

    /**
     * Report this server and read back the live network, including this server. Called every few
     * seconds, so it doubles as the heartbeat: a server that stops calling drops out of the list.
     */
    List<LinkServer> sync(LinkServer self) throws Exception;

    /** Leave the network on shutdown, so no one is matched here while the server is going away. */
    void leave(String serverId) throws Exception;

    @Override
    void close();
}
