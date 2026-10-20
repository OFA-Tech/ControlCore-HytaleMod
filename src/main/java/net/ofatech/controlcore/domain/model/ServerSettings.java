package net.ofatech.controlcore.domain.model;

import java.time.Duration;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * The server's main configuration as shown in the control panel.
 *
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
}
