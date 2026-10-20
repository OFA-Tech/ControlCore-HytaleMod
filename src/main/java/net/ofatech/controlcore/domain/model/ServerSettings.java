package net.ofatech.controlcore.domain.model;

import java.time.Duration;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * The server's main configuration as shown in the control panel.
 *
 * @param maxPlayers player limit; zero or less means unlimited
 * @param maxViewRadius view distance limit in chunks
 * @param passwordProtected whether joining requires a password (the password itself is never exposed)
 * @param playTimeout how long a connected player may stay unresponsive before being disconnected
 */
public record ServerSettings(
        String serverName,
        String motd,
        boolean passwordProtected,
        int maxPlayers,
        int maxViewRadius,
        Duration playTimeout) {

    public ServerSettings {
        Guard.notNull(serverName, "serverName");
        Guard.notNull(motd, "motd");
        Guard.notNull(playTimeout, "playTimeout");
    }

    public ServerSettings withServerName(String value) {
        return new ServerSettings(value, motd, passwordProtected, maxPlayers, maxViewRadius, playTimeout);
    }

    public ServerSettings withMotd(String value) {
        return new ServerSettings(serverName, value, passwordProtected, maxPlayers, maxViewRadius, playTimeout);
    }

    public ServerSettings withMaxPlayers(int value) {
        return new ServerSettings(serverName, motd, passwordProtected, value, maxViewRadius, playTimeout);
    }

    public ServerSettings withMaxViewRadius(int value) {
        return new ServerSettings(serverName, motd, passwordProtected, maxPlayers, value, playTimeout);
    }

    public ServerSettings withPlayTimeout(Duration value) {
        return new ServerSettings(serverName, motd, passwordProtected, maxPlayers, maxViewRadius, value);
    }
}
