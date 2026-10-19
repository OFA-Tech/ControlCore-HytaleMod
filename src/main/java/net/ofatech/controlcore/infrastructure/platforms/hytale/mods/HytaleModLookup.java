package net.ofatech.controlcore.infrastructure.platforms.hytale.mods;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;

public final class HytaleModLookup implements ModLookup {

    @Override
    public boolean isLoaded(String modId) {
        PluginIdentifier id;
        try {
            id = PluginIdentifier.fromString(modId);
        } catch (RuntimeException e) {
            return false;
        }
        PluginBase plugin = PluginManager.get().getPlugin(id);
        return plugin != null && !plugin.getState().isInactive();
    }
}
