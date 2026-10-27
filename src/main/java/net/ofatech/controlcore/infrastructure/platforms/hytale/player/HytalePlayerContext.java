package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
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

    private static final Duration FALLBACK_DELAY = Duration.ofSeconds(5);

    private final Map<PlayerId, Context> known = new ConcurrentHashMap<>();
    private final Map<PlayerId, Instant> lastAction = new ConcurrentHashMap<>();
    private final Map<PlayerId, Instant> combatUntil = new ConcurrentHashMap<>();

    @Override
    public Context current(PlayerId player) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        if (entity == null || !entity.isValid()) {
            known.remove(player);
            lastAction.remove(player);
            combatUntil.remove(player);
            return Context.UNKNOWN;
        }
        Store<EntityStore> store = entity.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            try {
                if (entity.isValid()) {
                    known.put(player, read(player, world, store, entity));
                }
            } catch (RuntimeException ignored) {
            }
        });
        return known.getOrDefault(player, new Context(world.getName(), "", false));
    }

    private Context read(PlayerId player, World world, Store<EntityStore> store, Ref<EntityStore> entity) {
        Player component = store.getComponent(entity, Player.getComponentType());
        GameMode mode = component == null ? null : component.getGameMode();
        DamageDataComponent damage = store.getComponent(entity, DamageDataComponent.getComponentType());
        Instant action = damage == null ? null : damage.getLastCombatAction();
        Instant now = Instant.now();
        if (action != null) {
            Instant before = lastAction.put(player, action);
            if (before != null && !before.equals(action)) {
                Duration delay = world.getGameplayConfig().getCombatConfig().getOutOfCombatDelay();
                combatUntil.put(player, now.plus(delay == null || delay.isZero() ? FALLBACK_DELAY : delay));
            }
        }
        boolean combat = combatUntil.getOrDefault(player, Instant.MIN).isAfter(now);
        return new Context(world.getName(), mode == null ? "" : mode.name().toLowerCase(Locale.ROOT), combat);
    }
}
