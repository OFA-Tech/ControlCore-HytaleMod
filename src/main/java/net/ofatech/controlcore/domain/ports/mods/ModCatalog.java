package net.ofatech.controlcore.domain.ports.mods;

import java.util.List;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.result.Result;

public interface ModCatalog {

    List<ModInfo> mods();

    Result<ModInfo> setEnabled(String modId, boolean enabled);

    List<ConfigFile> configFiles(String modId);

    record ConfigFile(String name, List<String> lines) {
        public ConfigFile {
            lines = List.copyOf(lines);
        }
    }
}
