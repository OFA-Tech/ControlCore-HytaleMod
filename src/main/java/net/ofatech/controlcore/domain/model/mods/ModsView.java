package net.ofatech.controlcore.domain.model.mods;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.result.Result;

/** The Mods tab as one viewer sees it. */
public interface ModsView {

    /** @return the mods the viewer may see, sorted by name; Hytale's own included (the tab hides them) */
    List<Entry> list();

    /** @return whether the viewer may turn mods on and off */
    boolean canManage();

    /** Turns a mod on or off from the next start. */
    Result<Entry> setEnabled(String modId, boolean enabled);

    /** @return the mod's config files, read-only */
    List<ModCatalog.ConfigFile> configFiles(String modId);

    /**
     * One mod as listed.
     *
     * @param mod the mod
     * @param namespace the id its settings and keys use ({@code mod:<namespace>} in searches), if it has any
     * @param hasServerSettings whether it has server settings
     * @param hasPreferences whether it has player preferences
     * @param hasKeys whether it has key actions
     * @param manageable whether the viewer may turn it on and off
     */
    record Entry(
            ModInfo mod,
            Optional<String> namespace,
            boolean hasServerSettings,
            boolean hasPreferences,
            boolean hasKeys,
            boolean manageable) {}
}
