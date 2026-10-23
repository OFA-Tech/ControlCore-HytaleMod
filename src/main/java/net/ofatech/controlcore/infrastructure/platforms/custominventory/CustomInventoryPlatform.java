package net.ofatech.controlcore.infrastructure.platforms.custominventory;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.controlpanel.ControlPanelService;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;
import net.ofatech.controlcore.domain.ports.platform.CapabilityBinder;
import net.ofatech.controlcore.domain.ports.platform.ModPlatform;
import net.ofatech.controlcore.domain.ports.system.Log;

@AutoService(ModPlatform.class)
public final class CustomInventoryPlatform implements ModPlatform {

    static final String MOD_ID = "SupremoSama:CustomInventory";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void bind(CapabilityBinder binder) {
        Log log = binder.service(Log.class);
        ClassLoader modClasses = binder.service(ModLookup.class)
                .classLoader(MOD_ID)
                .orElseThrow(() -> new IllegalStateException(MOD_ID + " is not loaded"));
        AutoCloseable button =
                CustomInventoryButton.install(modClasses, binder.service(ControlPanelService.class), log);
        binder.onRelease(() -> {
            try {
                button.close();
            } catch (Exception e) {
                log.warn("Could not remove the ControlCore button from CustomInventory: " + e);
            }
        });
    }
}
