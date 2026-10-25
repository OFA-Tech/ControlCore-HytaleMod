package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerTuning;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytalePlayerTuning implements PlayerTuning {

    private record ChunkDefaults(int perSecond, int perTick) {}

    private final Log log;
    private final Map<UUID, ChunkDefaults> chunkDefaults = new ConcurrentHashMap<>();

    public HytalePlayerTuning(Log log) {
        this.log = log;
    }

    @Override
    public Optional<String> worldOf(PlayerId player) {
        PlayerRef ref = Universe.get().getPlayer(player.value());
        if (ref == null || ref.getWorldUuid() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Universe.get().getWorld(ref.getWorldUuid())).map(World::getName);
    }

    @Override
    public void apply(PlayerId player, Tuning tuning) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        if (playerRef == null || playerRef.getWorldUuid() == null) {
            return;
        }
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                applyChunks(playerRef, tuning);
                applyMovement(playerRef, tuning);
            } catch (RuntimeException e) {
                log.error("Could not apply speed and chunk limits to " + playerRef.getUsername(), e);
            }
        });
    }

    private void applyChunks(PlayerRef playerRef, Tuning tuning) {
        ChunkTracker tracker = playerRef.getChunkTracker();
        if (tracker == null) {
            return;
        }
        ChunkDefaults defaults = chunkDefaults.computeIfAbsent(
                playerRef.getUuid(),
                id -> new ChunkDefaults(tracker.getMaxSectionsPerSecond(), tracker.getMaxSectionsPerTick()));
        int perSecond = tuning.sectionsPerSecond() > 0 ? tuning.sectionsPerSecond() : defaults.perSecond();
        int perTick = tuning.sectionsPerTick() > 0 ? tuning.sectionsPerTick() : defaults.perTick();
        if (tracker.getMaxSectionsPerSecond() != perSecond) {
            tracker.setMaxSectionsPerSecond(perSecond);
        }
        if (tracker.getMaxSectionsPerTick() != perTick) {
            tracker.setMaxSectionsPerTick(perTick);
        }
    }

    private void applyMovement(PlayerRef playerRef, Tuning tuning) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        MovementManager movement = store.getComponent(ref, MovementManager.getComponentType());
        if (movement == null || movement.getDefaultSettings() == null || movement.getSettings() == null) {
            return;
        }
        MovementSettings defaults = movement.getDefaultSettings();
        MovementSettings settings = movement.getSettings();
        float speed = defaults.baseSpeed * tuning.speedPercent() / 100f;
        float jump = defaults.jumpForce * tuning.jumpPercent() / 100f;
        if (settings.baseSpeed != speed || settings.jumpForce != jump) {
            settings.baseSpeed = speed;
            settings.jumpForce = jump;
            movement.update(playerRef.getPacketHandler());
        }
    }
}
