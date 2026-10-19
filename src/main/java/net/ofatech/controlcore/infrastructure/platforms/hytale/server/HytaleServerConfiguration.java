package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;

public final class HytaleServerConfiguration implements ServerConfiguration {

    @Override
    public ServerSettings read() {
        HytaleServerConfig config = HytaleServer.get().getConfig();
        String password = config.getPassword();
        return new ServerSettings(
                nonNull(config.getServerName()),
                nonNull(config.getMotd()),
                password != null && !password.isEmpty(),
                config.getMaxPlayers(),
                config.getMaxViewRadius());
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }
}
