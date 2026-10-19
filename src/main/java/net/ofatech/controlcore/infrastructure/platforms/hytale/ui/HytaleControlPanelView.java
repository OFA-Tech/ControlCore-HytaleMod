package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/** Built-in {@link ControlPanelView}: opens {@link ControlPanelPage} through Hytale's page manager. */
public final class HytaleControlPanelView implements ControlPanelView {

    private final Log log;

    public HytaleControlPanelView(Log log) {
        this.log = log;
    }

    @Override
    public Result<Void> open(PlayerId player, ControlPanelRequest request, ControlPanelContent content) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        if (entity == null || !entity.isValid()) {
            return Result.failure(Error.notFound("ControlPanel.PlayerNotFound", "That player is not in a world."));
        }
        Store<EntityStore> store = entity.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            try {
                Player component = store.getComponent(entity, Player.getComponentType());
                if (component != null && entity.isValid()) {
                    component.getPageManager().openCustomPage(entity, store, new ControlPanelPage(playerRef, content));
                }
            } catch (RuntimeException e) {
                log.error("Could not open the control panel for " + playerRef.getUsername(), e);
            }
        });
        return Result.success();
    }
}
