package net.ofatech.controlcore.domain.model.mods;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.result.Result;

public interface ModsView {

    List<Entry> list();

    boolean canManage();

    Result<Entry> setEnabled(String modId, boolean enabled);

    List<ModCatalog.ConfigFile> configFiles(String modId);

    record Entry(
            ModInfo mod,
            Optional<String> namespace,
            boolean hasServerSettings,
            boolean hasPreferences,
            boolean hasKeys,
            boolean manageable) {}
}
