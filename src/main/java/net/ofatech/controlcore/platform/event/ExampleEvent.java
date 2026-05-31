package net.ofatech.controlcore.platform.event;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.ofatech.controlcore.ControlCore;
import net.ofatech.controlcore.core.domain.interfaces.IModEvent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class ExampleEvent implements IModEvent {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public void register(ControlCore plugin) {
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();

        Ref<EntityStore> entityRef = player.getReference();
        Objects.requireNonNull(entityRef, "entityRef cannot be null");

        Store<EntityStore> store = entityRef.getStore();
        Objects.requireNonNull(store, "store cannot be null");

        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        Objects.requireNonNull(playerRef, "displayName cannot be null");

        DisplayNameComponent nameComponent = store.getComponent(entityRef, DisplayNameComponent.getComponentType());
        Objects.requireNonNull(nameComponent, "displayName cannot be null");
        if (nameComponent.getDisplayName() == null) {
            return;
        }
        String displayName = nameComponent.getDisplayName().getRawText();

        playerRef.sendMessage(Message.raw("Welcome " + displayName));
    }
}
