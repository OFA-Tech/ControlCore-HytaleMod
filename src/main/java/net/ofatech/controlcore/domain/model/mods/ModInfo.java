package net.ofatech.controlcore.domain.model.mods;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * A mod (plugin) or asset pack the server has, as its manifest describes it.
 *
 * @param id {@code Group:Name}
 * @param name the manifest's name
 * @param group the manifest's group
 * @param version its version, may be empty
 * @param description may be empty
 * @param authors author names
 * @param website may be empty
 * @param kind code, assets or both
 * @param state whether it runs now
 * @param core whether it is part of Hytale itself (group {@code Hytale})
 * @param enabledAfterRestart whether it will run after the next restart (the server config's choice)
 * @param logo path of its logo under {@code Common/UI/Custom/}, if it ships one ({@code Group_Name.png})
 */
public record ModInfo(
        String id,
        String name,
        String group,
        String version,
        String description,
        List<String> authors,
        String website,
        Kind kind,
        State state,
        boolean core,
        boolean enabledAfterRestart,
        Optional<String> logo) {

    public ModInfo {
        Guard.notBlank(id, "id");
        Guard.notNull(name, "name");
        Guard.notNull(group, "group");
        version = version == null ? "" : version;
        description = description == null ? "" : description.strip();
        authors = authors == null ? List.of() : List.copyOf(authors);
        website = website == null ? "" : website.strip();
        Guard.notNull(kind, "kind");
        Guard.notNull(state, "state");
        logo = logo == null ? Optional.empty() : logo;
    }

    /** Code, assets or both. */
    public enum Kind {
        PLUGIN,
        ASSET_PACK,
        PLUGIN_WITH_ASSETS
    }

    /** Whether it runs now. */
    public enum State {
        ENABLED,
        DISABLED,
        FAILED,
        NOT_LOADED
    }

    /** @return whether it runs now */
    public boolean running() {
        return state == State.ENABLED;
    }

    /** @return whether a restart changes whether it runs */
    public boolean changesAfterRestart() {
        return enabledAfterRestart != running() && state != State.FAILED;
    }

    /** @return whether it brings assets */
    public boolean hasAssets() {
        return kind != Kind.PLUGIN;
    }
}
