package net.ofatech.controlcore.infrastructure.platforms.hytale.event;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Ids;

public final class HytaleGameEvents {

    private final EventRegistry registry;
    private final ComponentRegistryProxy<EntityStore> entities;
    private final EventPublisher publisher;

    public HytaleGameEvents(
            EventRegistry registry, ComponentRegistryProxy<EntityStore> entities, EventPublisher publisher) {
        this.registry = registry;
        this.entities = entities;
        this.publisher = publisher;
    }

    public void register() {
        registry.registerGlobal(PlayerReadyEvent.class, (PlayerReadyEvent event) -> onReady(event));
        registry.register(PlayerDisconnectEvent.class, (PlayerDisconnectEvent event) -> onDisconnect(event));
        entities.registerSystem(new GameModeChangeSystem(publisher));
    }

    private void onReady(PlayerReadyEvent event) {
        Ref<EntityStore> entity = event.getPlayerRef();
        if (entity == null || !entity.isValid()) {
            return;
        }
        Store<EntityStore> store = entity.getStore();
        PlayerRef ref = store.getComponent(entity, PlayerRef.getComponentType());
        if (ref != null) {
            publisher.publish(new PlayerJoined(Ids.player(ref)));
        }
    }

    private void onDisconnect(PlayerDisconnectEvent event) {
        PlayerRef ref = event.getPlayerRef();
        if (ref != null) {
            publisher.publish(new PlayerLeft(Ids.player(ref)));
        }
    }
}
