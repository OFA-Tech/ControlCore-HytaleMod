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
        if (!controlPanel.isAvailable()) {
            context.log().info("Control panel is disabled (setting " + ControlPanelSettings.ENABLED_ID + ")");
        } else {
            context.log().info("Control panel is available (built-in Hytale UI): /open-admin-config");
        }
    }
}
