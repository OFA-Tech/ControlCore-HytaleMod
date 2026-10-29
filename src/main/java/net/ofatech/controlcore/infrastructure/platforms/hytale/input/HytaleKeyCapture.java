package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.KeyCapture;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleKeyCapture implements KeyCapture {

    private final Log log;

    public HytaleKeyCapture(Log log) {
        this.log = log;
    }

    @Override
    public void open(PlayerId player, Consumer<Optional<String>> done) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        if (entity == null || !entity.isValid()) {
            done.accept(Optional.empty());
            return;
        }
        Store<EntityStore> store = entity.getStore();
        store.getExternalData().getWorld().execute(() -> {
            try {
                Player component = store.getComponent(entity, Player.getComponentType());
                if (component != null && entity.isValid()) {
                    component.getPageManager().openCustomPage(entity, store, new KeyCapturePage(playerRef, done));
                } else {
                    done.accept(Optional.empty());
                }
            } catch (RuntimeException e) {
                log.error("Could not open the key capture screen for " + playerRef.getUsername(), e);
                done.accept(Optional.empty());
            }
        });
    }
}
