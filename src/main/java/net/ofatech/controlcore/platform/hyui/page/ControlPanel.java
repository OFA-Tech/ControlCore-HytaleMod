package net.ofatech.controlcore.platform.hyui.page;

import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.ofatech.controlcore.core.domain.models.ControlPanelRequest;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ControlPanel {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String HTML_PATH = "/Pages/control-panel.html";


    public static CompletableFuture<Void> open(Player player, ControlPanelRequest request) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        player.getWorldMapTracker().tick(0);
        Ref<EntityStore> entityRef = player.getReference();

        if (entityRef == null || !entityRef.isValid()){
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();

        var displayName = store.getComponent(entityRef, DisplayNameComponent.getComponentType());
        Objects.requireNonNull(displayName, "displayName cannot be null");

        return CompletableFuture.runAsync(() -> {
            try {
                PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
                if (playerRef == null) {
                    LOGGER.atWarning().log("PlayerRef component not found for player %s in world %s".formatted(displayName.getDisplayName(), world.getName()));
                    return;
                }

                PageBuilder.pageForPlayer(playerRef)
                    .loadHtml(HTML_PATH)
                    .enableAsyncImageLoading(true)
                    .addEventListener("close-btn", CustomUIEventBindingType.Activating, (ignored, ctx) ->
                        ctx.getPage().ifPresent(HyUIPage::close)
                    )
                    .open(store);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to open control panel for player %s: %s", displayName.getDisplayName(), e.getMessage());
            }
        }, world);
    }
}
