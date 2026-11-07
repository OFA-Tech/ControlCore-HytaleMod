package net.ofatech.controlcore.application.maintenance;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;

@AutoService(FeatureModule.class)
public final class MaintenanceModule implements FeatureModule {

    private final MaintenanceService maintenance;

    public MaintenanceModule(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    public void start(ModuleContext context) {
        maintenance.restoreOnStart();
    }
}
