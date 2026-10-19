package net.ofatech.controlcore.domain.model;

import net.ofatech.controlcore.domain.guard.Guard;

/**
 * The server's main configuration as shown in the control panel.
 *
 * @param passwordProtected whether joining requires a password (the password itself is never exposed)
 */
public record ServerSettings(
        String serverName, String motd, boolean passwordProtected, int maxPlayers, int maxViewRadius) {

    public ServerSettings {
        Guard.notNull(serverName, "serverName");
        Guard.notNull(motd, "motd");
    }
}
