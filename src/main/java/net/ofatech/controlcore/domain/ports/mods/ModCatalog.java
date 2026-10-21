package net.ofatech.controlcore.domain.ports.mods;

import java.util.List;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.result.Result;

/** The mods and asset packs the server has, and whether they run after a restart. */
public interface ModCatalog {

    /** @return every mod and asset pack, Hytale's own included */
    List<ModInfo> mods();

    /**
     * Turns a mod on or off for the next start, in the server's own config.
     *
     * @param modId {@code Group:Name}
     */
    Result<ModInfo> setEnabled(String modId, boolean enabled);

    /**
     * @param modId {@code Group:Name}
     * @return the mod's config files in its data folder, as {@code key = value} lines (read-only)
     */
    List<ConfigFile> configFiles(String modId);

    /**
     * A mod's config file.
     *
     * @param name file name, for example {@code config.json}
     * @param lines its values, {@code path.to.key = value}, possibly cut short
     */
    record ConfigFile(String name, List<String> lines) {
        public ConfigFile {
            lines = List.copyOf(lines);
        }
    }
}
