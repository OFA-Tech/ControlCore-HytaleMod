package net.ofatech.controlcore.infrastructure.platforms.hytale.pages;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytalePages {

    private HytalePages() {}

    public static Optional<PlayerRef> inWorld(UUID player) {
        PlayerRef playerRef = Universe.get().getPlayer(player);
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        return entity != null && entity.isValid() ? Optional.of(playerRef) : Optional.empty();
    }

    public static boolean open(
            PlayerRef playerRef, Function<PlayerRef, CustomUIPage> page, Log log, String what, Runnable failed) {
        Ref<EntityStore> entity = playerRef.getReference();
        if (entity == null || !entity.isValid()) {
            return false;
        }
        Store<EntityStore> store = entity.getStore();
        store.getExternalData().getWorld().execute(() -> {
            try {
                Player component = store.getComponent(entity, Player.getComponentType());
                if (component != null && entity.isValid()) {
                    component.getPageManager().openCustomPage(entity, store, page.apply(playerRef));
                } else {
                    failed.run();
                }
            } catch (RuntimeException e) {
                log.error("Could not open " + what + " for " + playerRef.getUsername(), e);
                failed.run();
            }
        });
        return true;
    }
}
