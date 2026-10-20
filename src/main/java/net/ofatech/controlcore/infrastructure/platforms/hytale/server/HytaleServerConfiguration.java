package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.setup.ViewRadius;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Duration;
import java.util.Objects;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Reads and edits {@link HytaleServerConfig}. Writing mirrors Hytale's own {@code /maxplayers} and
 * {@code /maxviewradius} commands: setters mark the config changed, the view radius is pushed to online players
 * on their world threads, and the config is saved to {@code config.json} at once.
 */
public final class HytaleServerConfiguration implements ServerConfiguration {

    private static final int CHUNK_SIZE = 32;

    private final Log log;

    public HytaleServerConfiguration(Log log) {
        this.log = log;
    }

    @Override
    public ServerSettings read() {
        HytaleServerConfig config = HytaleServer.get().getConfig();
        return toSettings(config, playTimeout(config));
    }

    @Override
    public ServerSettings defaults() {
        HytaleServerConfig fresh = new HytaleServerConfig();
        Duration play = HytaleServerConfig.TimeoutProfile.defaults().getPlay();
        return toSettings(fresh, play != null ? play : Duration.ZERO);
    }

    @Override
    public synchronized Result<ServerSettings> write(ServerSettings settings) {
        HytaleServerConfig config = HytaleServer.get().getConfig();
        try {
            if (!settings.serverName().equals(nonNull(config.getServerName()))) {
                config.setServerName(settings.serverName());
            }
            if (!settings.motd().equals(nonNull(config.getMotd()))) {
                config.setMotd(settings.motd());
            }
            if (settings.maxPlayers() != config.getMaxPlayers()) {
                config.setMaxPlayers(settings.maxPlayers());
            }
            if (settings.maxViewRadius() != config.getMaxViewRadius()) {
                config.setMaxViewRadius(settings.maxViewRadius());
                applyViewRadius(settings.maxViewRadius());
            }
            if (!settings.playTimeout().equals(playTimeout(config))) {
                HytaleServerConfig.TimeoutProfile timeouts = config.getConnectionTimeouts();
                if (timeouts == null) {
                    timeouts = new HytaleServerConfig.TimeoutProfile(config);
                    config.setConnectionTimeouts(timeouts);
                }
                timeouts.setPlay(settings.playTimeout());
                config.markChanged();
            }
        } catch (RuntimeException e) {
            log.error("Could not change the server config", e);
            return Result.failure(Error.failure("ServerConfig.WriteFailed", "The server rejected the change."));
        }
        if (config.consumeHasChanged()) {
            HytaleServerConfig.save(config).exceptionally(failure -> {
                log.error("Could not save the server config", failure);
                return null;
            });
        }
        return Result.success(read());
    }

    private void applyViewRadius(int radius) {
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (PlayerRef playerRef : world.getPlayerRefs()) {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    Player player = store.getComponent(ref, Player.getComponentType());
                    EntityTrackerSystems.EntityViewer viewer =
                            store.getComponent(ref, EntityTrackerSystems.EntityViewer.getComponentType());
                    if (player != null && viewer != null) {
                        viewer.viewRadiusBlocks = player.getViewRadius() * CHUNK_SIZE;
                        playerRef.getPacketHandler().writeNoCache(new ViewRadius(radius));
                    }
                }
            });
        }
    }

    private static ServerSettings toSettings(HytaleServerConfig config, Duration play) {
        String password = config.getPassword();
        return new ServerSettings(
                nonNull(config.getServerName()),
                nonNull(config.getMotd()),
                password != null && !password.isEmpty(),
                config.getMaxPlayers(),
                config.getMaxViewRadius(),
                play);
    }

    private static Duration playTimeout(HytaleServerConfig config) {
        HytaleServerConfig.TimeoutProfile timeouts = config.getConnectionTimeouts();
        Duration play = timeouts != null ? timeouts.getPlay() : null;
        return Objects.requireNonNullElse(play, Duration.ZERO);
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }
}
