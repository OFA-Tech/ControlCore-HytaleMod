package net.ofatech.controlcore.infrastructure.platforms.hytale.pages;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Messages;

public final class HytalePages {

    private HytalePages() {}

    public static Optional<PlayerRef> inWorld(UUID player) {
        PlayerRef playerRef = Universe.get().getPlayer(player);
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        return entity != null && entity.isValid() ? Optional.of(playerRef) : Optional.empty();
    }

    public enum Reopen {
        DONE,
        GONE,
        DEAD,
        BUSY
    }

    public static void onWorldThread(PlayerRef playerRef, Runnable work) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref != null && ref.isValid()) {
            ref.getStore().getExternalData().getWorld().execute(work);
        }
    }

    public static Reopen reopenIfFree(PlayerRef playerRef, CustomUIPage page) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return Reopen.GONE;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return Reopen.GONE;
        }
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            return Reopen.DEAD;
        }
        CustomUIPage current = player.getPageManager().getCustomPage();
        if (current == page) {
            return Reopen.DONE;
        }
        if (current != null
                || player.getPageManager().getActiveServersidePage() != null
                || !player.getWindowManager().getWindows().isEmpty()) {
            return Reopen.BUSY;
        }
        player.getPageManager().openCustomPage(ref, store, page);
        return Reopen.DONE;
    }

    public static void tell(PlayerRef playerRef, String text) {
        playerRef.sendMessage(Messages.text(text));
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
