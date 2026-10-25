package net.ofatech.controlcore.application.worlds;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;

@AutoService(FeatureModule.class)
public final class WorldsModule implements FeatureModule {

    private final WorldsService worlds;

    public WorldsModule(WorldsService worlds) {
        this.worlds = worlds;
    }

    @Override
    public void start(ModuleContext context) {
        worlds.start();
    }

    @Override
    public void shutdown() {
        worlds.stop();
    }
}
