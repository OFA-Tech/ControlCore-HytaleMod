package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import java.time.Duration;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;

public final class HytaleServerConfiguration implements ServerConfiguration {

    @Override
    public ServerSettings read() {
        HytaleServerConfig config = HytaleServer.get().getConfig();
        String password = config.getPassword();
        HytaleServerConfig.TimeoutProfile timeouts = config.getConnectionTimeouts();
        Duration play = timeouts != null ? timeouts.getPlay() : null;
        return new ServerSettings(
                nonNull(config.getServerName()),
                nonNull(config.getMotd()),
                password != null && !password.isEmpty(),
                config.getMaxPlayers(),
                config.getMaxViewRadius(),
                play != null ? play : Duration.ZERO);
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }
}
