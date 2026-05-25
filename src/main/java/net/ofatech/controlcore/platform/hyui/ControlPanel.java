package net.ofatech.controlcore.platform.hyui;

import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.ofatech.controlcore.core.domain.interfaces.IControlPanel;
import net.ofatech.controlcore.core.domain.models.ControlPanelRequest;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class ControlPanel implements IControlPanel {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final String HTML_STRING = """
            <div class="page-overlay">
                <div class="container" data-hyui-title="ControlCore">
                    <div class="container-contents">
                        <p>ControlCore admin panel</p>
                        <button id="close-btn">Close</button>
                    </div>
                </div>
            </div>
            """;


    @Override
    public CompletableFuture<Void> openControlPanel(Player player, ControlPanelRequest request) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        player.getWorldMapTracker().tick(0);
        Ref<EntityStore> entityRef = player.getReference();

        if (entityRef == null || !entityRef.isValid()){
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            try {
                PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
                if (playerRef == null) {
                    LOGGER.atWarning().log("PlayerRef component not found for player %s in world %s".formatted(player.getDisplayName(), world.getName()));
                    return;
                }

                PageBuilder.pageForPlayer(playerRef)
                    .fromHtml(HTML_STRING)
                    .addEventListener("close-btn", CustomUIEventBindingType.Activating, (ignored, ctx) ->
                        ctx.getPage().ifPresent(HyUIPage::close)
                    )
                    .open(store);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to open control panel for player %s: %s", player.getDisplayName(), e.getMessage());
            }
        }, world);
    }
}
