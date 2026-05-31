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


    public static CompletableFuture<Void> open(Ref<EntityStore> entityRef, ControlPanelRequest request) {
        Objects.requireNonNull(entityRef, "entityRef cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        if (!entityRef.isValid()){
            LOGGER.atWarning().log("Invalid entityRef: %s".formatted(entityRef));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            try {
                Player player = store.getComponent(entityRef, Player.getComponentType());
                if (player == null) {
                    throw new IllegalStateException("Player component not found for entityRef: " + entityRef);
                }
                DisplayNameComponent nameComponent = store.getComponent(entityRef, DisplayNameComponent.getComponentType());
                Objects.requireNonNull(nameComponent, "displayName cannot be null");
                if (nameComponent.getDisplayName() == null) {
                    throw new IllegalStateException("displayName cannot be null");
                }
                String displayName = nameComponent.getDisplayName().getRawText();

                PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
                if (playerRef == null) {
                    LOGGER.atWarning().log("PlayerRef component not found for player %s in world %s".formatted(displayName, world.getName()));
                    throw new IllegalStateException("PlayerRef component not found for player " + displayName + " in world " + world.getName());
                }

                PageBuilder.pageForPlayer(playerRef)
                    .loadHtml(HTML_PATH)
                    .enableAsyncImageLoading(true)
                    .addEventListener("close-btn", CustomUIEventBindingType.Activating, (ignored, ctx) ->
                        ctx.getPage().ifPresent(HyUIPage::close)
                    )
                    .open(store);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to open control panel: %s", e.getMessage());
            }
        }, world);
    }
}
