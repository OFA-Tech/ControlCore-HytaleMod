package net.ofatech.controlcore.application.controlpanel;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;

@AutoService(FeatureModule.class)
public final class ControlPanelModule implements FeatureModule {

    private final ControlPanelService controlPanel;

    public ControlPanelModule(ControlPanelService controlPanel) {
        this.controlPanel = controlPanel;
    }

    @Override
    public void start(ModuleContext context) {
        if (!context.settings().current().controlPanelEnabled()) {
            context.log().info("Control panel is disabled in Settings.json");
        } else if (controlPanel.isAvailable()) {
            context.log().info("Control panel is available: /open-admin-config");
        } else {
            context.log().warn("Control panel is unavailable: install HyUI (Ellie:HyUI) to use /open-admin-config");
        }
    }
}
