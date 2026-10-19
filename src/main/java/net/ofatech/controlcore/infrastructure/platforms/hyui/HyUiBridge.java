package net.ofatech.controlcore.infrastructure.platforms.hyui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.platform.CapabilityBinder;
import net.ofatech.controlcore.domain.ports.system.Log;

final class HyUiBridge {

    /**
     * HyUI 0.9.x updates open pages through this method. Hytale 0.7 renamed it to
     * {@code updateLegacyCustomPage}, and the resulting NoSuchMethodError on the world thread crashes the
     * world as soon as a page changes (for example on a tab click). Only bind when the server still has it.
     */
    private static final String HYUI_UPDATE_METHOD = "updateCustomPage";

    private HyUiBridge() {}

    static void bind(CapabilityBinder binder) {
        // Touch a HyUI class now, so an incompatible HyUI fails here (and is rolled back) instead of on first use.
        PageBuilder.class.getName();
        requireServerApi();
        Log log = binder.service(Log.class);
        binder.provide(ControlPanelView.class, () -> new HyUiControlPanel(log));
    }

    private static void requireServerApi() {
        try {
            PageManager.class.getMethod(HYUI_UPDATE_METHOD, CustomPage.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("This HyUI version is not compatible with this Hytale server: "
                    + "PageManager." + HYUI_UPDATE_METHOD + " no longer exists, so HyUI pages would crash the world. "
                    + "Update HyUI to a version that supports this server.");
        }
    }
}
