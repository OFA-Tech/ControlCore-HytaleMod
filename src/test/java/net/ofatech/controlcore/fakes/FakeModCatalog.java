package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class FakeModCatalog implements ModCatalog {

    public final List<ModInfo> mods = new CopyOnWriteArrayList<>();
    public final Map<String, List<ConfigFile>> files = new ConcurrentHashMap<>();

    public ModInfo add(String id, boolean core) {
        String[] parts = id.split(":", 2);
        ModInfo mod = new ModInfo(
                id,
                parts[1],
                parts[0],
                "1.0.0",
                "",
                List.of("Someone"),
                "",
                ModInfo.Kind.PLUGIN,
                ModInfo.State.ENABLED,
                core,
                true,
                Optional.empty());
        mods.add(mod);
        return mod;
    }

    @Override
    public List<ModInfo> mods() {
        return List.copyOf(mods);
    }

    @Override
    public Result<ModInfo> setEnabled(String modId, boolean enabled) {
        for (int index = 0; index < mods.size(); index++) {
            ModInfo mod = mods.get(index);
            if (mod.id().equals(modId)) {
                ModInfo changed = new ModInfo(
                        mod.id(),
                        mod.name(),
                        mod.group(),
                        mod.version(),
                        mod.description(),
                        mod.authors(),
                        mod.website(),
                        mod.kind(),
                        mod.state(),
                        mod.core(),
                        enabled,
                        mod.logo());
                mods.set(index, changed);
                return Result.success(changed);
            }
        }
        return Result.failure(Error.notFound("Mods.Unknown", "no " + modId));
    }

    @Override
    public List<ConfigFile> configFiles(String modId) {
        return files.getOrDefault(modId, List.of());
    }
}
