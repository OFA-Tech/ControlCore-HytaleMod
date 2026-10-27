package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerContext;

public final class HytalePlayerContext implements PlayerContext {

    private final Map<PlayerId, Context> known = new ConcurrentHashMap<>();

    @Override
    public Context current(PlayerId player) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        if (entity == null || !entity.isValid()) {
            known.remove(player);
            return Context.UNKNOWN;
        }
        Store<EntityStore> store = entity.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            try {
                if (entity.isValid()) {
                    known.put(player, read(world, store, entity));
                }
            } catch (RuntimeException ignored) {
            }
        });
        return known.getOrDefault(player, new Context(world.getName(), "", false));
    }

    private static Context read(World world, Store<EntityStore> store, Ref<EntityStore> entity) {
        Player component = store.getComponent(entity, Player.getComponentType());
        GameMode mode = component == null ? null : component.getGameMode();
        DamageDataComponent damage = store.getComponent(entity, DamageDataComponent.getComponentType());
        boolean combat = false;
        if (damage != null && damage.getLastCombatAction() != null) {
            Instant now = store.getResource(TimeResource.getResourceType()).getNow();
            Duration delay = world.getGameplayConfig().getCombatConfig().getOutOfCombatDelay();
            combat = now != null
                    && delay != null
                    && damage.getLastCombatAction().plus(delay).isAfter(now);
        }
        return new Context(world.getName(), mode == null ? "" : mode.name().toLowerCase(Locale.ROOT), combat);
    }
}
