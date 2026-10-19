package net.ofatech.controlcore.infrastructure.platforms.hyui;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.domain.ports.platform.CapabilityBinder;
import net.ofatech.controlcore.domain.ports.platform.ModPlatform;

/** HyUI (Ellie). Safe to load without the mod: HyUI classes are only reached in bind. */
@AutoService(ModPlatform.class)
public final class HyUiPlatform implements ModPlatform {

    @Override
    public String modId() {
        return "Ellie:HyUI";
    }

    @Override
    public void bind(CapabilityBinder binder) {
        HyUiBridge.bind(binder);
    }
}
