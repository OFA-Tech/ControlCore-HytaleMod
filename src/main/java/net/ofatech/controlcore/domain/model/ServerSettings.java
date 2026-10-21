package net.ofatech.controlcore.domain.model;

import java.time.Duration;
import net.ofatech.controlcore.domain.guard.Guard;

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
