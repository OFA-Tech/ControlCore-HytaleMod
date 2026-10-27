package net.ofatech.controlcore.domain.model.mods;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.result.Result;

public interface ModsView {

    List<Entry> list();

    boolean canManage();

    Result<Entry> setEnabled(String modId, boolean enabled);

    List<Entry> alsoChanged(String modId, boolean enabled);

    List<ModCatalog.ConfigFile> configFiles(String modId);

    record Entry(
            ModInfo mod,
            Optional<String> namespace,
            boolean hasServerSettings,
            boolean hasPreferences,
            boolean hasKeys,
            boolean manageable,
            List<String> neededBy) {

        public Entry {
            neededBy = neededBy == null ? List.of() : List.copyOf(neededBy);
        }

        public boolean hasProblems() {
            return !mod.serverVersionOk() || mod.dependencies().stream().anyMatch(ModDependency::problem);
        }
    }
}
