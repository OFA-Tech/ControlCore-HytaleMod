package net.ofatech.controlcore.infrastructure.platforms.hyui;

import au.ellie.hyui.builders.PageBuilder;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.platform.CapabilityBinder;
import net.ofatech.controlcore.domain.ports.system.Log;

final class HyUiBridge {

    private HyUiBridge() {}

    static void bind(CapabilityBinder binder) {
        // Touch a HyUI class now, so an incompatible HyUI fails here (and is rolled back) instead of on first use.
        PageBuilder.class.getName();
        Log log = binder.service(Log.class);
        binder.provide(ControlPanelView.class, () -> new HyUiControlPanel(log));
    }
}
