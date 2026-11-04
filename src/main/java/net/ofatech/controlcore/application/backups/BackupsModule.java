package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;

@AutoService(FeatureModule.class)
public final class BackupsModule implements FeatureModule {

    private final BackupService backups;

    public BackupsModule(BackupService backups) {
        this.backups = backups;
    }

    @Override
    public void setup(ModuleContext context) {
        backups.applyPendingRestore();
    }
}
