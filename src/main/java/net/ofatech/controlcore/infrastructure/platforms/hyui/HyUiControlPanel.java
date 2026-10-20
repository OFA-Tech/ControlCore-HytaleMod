package net.ofatech.controlcore.infrastructure.platforms.hyui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.ControlPanelContent;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/** Inbound: HyUI → our {@link ControlPanelView}. Opens {@code Common/UI/Custom/Pages/control-panel-hyui.html}. */
final class HyUiControlPanel implements ControlPanelView {

    private static final String HTML_PATH = "/Pages/control-panel-hyui.html";

    private final Log log;

    HyUiControlPanel(Log log) {
        this.log = log;
    }

    @Override
    public Result<Void> open(PlayerId player, ControlPanelRequest request, ControlPanelContent content) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        if (entity == null || !entity.isValid()) {
            return Result.failure(Error.notFound("ControlPanel.PlayerNotFound", "That player is not in a world.")
                    .localized("controlcore.errors.controlpanel.playernotfound_2"));
        }
        Store<EntityStore> store = entity.getStore();
        World world = store.getExternalData().getWorld();
        CompletableFuture.runAsync(
                        () -> PageBuilder.pageForPlayer(playerRef)
                                .loadHtml(HTML_PATH)
                                .enableAsyncImageLoading(true)
                                .open(store),
                        world)
                .exceptionally(failure -> {
                    log.error("Could not open the control panel for " + playerRef.getUsername(), failure);
                    return null;
                });
        return Result.success();
    }
}
