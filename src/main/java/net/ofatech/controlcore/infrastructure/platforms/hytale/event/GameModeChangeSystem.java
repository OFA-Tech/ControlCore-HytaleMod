package net.ofatech.controlcore.infrastructure.platforms.hytale.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.ofatech.controlcore.domain.event.PlayerGameModeChanged;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Ids;

final class GameModeChangeSystem extends EntityEventSystem<EntityStore, ChangeGameModeEvent> {

    private final EventPublisher publisher;

    GameModeChangeSystem(EventPublisher publisher) {
        super(ChangeGameModeEvent.class);
        this.publisher = publisher;
    }

    @Override
    public void handle(
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commands,
            ChangeGameModeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player != null) {
            publisher.publish(new PlayerGameModeChanged(Ids.player(player)));
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
