package net.ofatech.controlcore.api;

import java.util.List;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The server's mods, as in the control panel's Mods tab and {@code /cc-mods}: details, dependencies, turning mods
 * on and off from the next start, and their config files. Get it with {@link ControlCoreApi#mods()}.
 *
 * <p>The API acts with full rights, like the console, and the change history shows the change as made by a mod.
 * {@link #actingFor(PlayerId)} gives the same API with a player's rights.
 *
 * @since API 2.4.0
 */
public interface ModsApi {

    /**
     * Returns this API acting for a player, with the rights they have in the Mods tab: players who are not admins
     * do not see the mods in {@code controlcore:mods-hidden} and may not turn mods on or off
     * ({@code Mods.Forbidden}). The change history records the player as the one who made the change.
     *
     * @param player the player whose rights apply
     * @return the mods API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    ModsApi actingFor(PlayerId player);

    /**
     * Lists the server's mods and asset packs.
     *
     * @return the mods this API may see, sorted by name
     */
    List<Mod> list();

    /**
     * Lists the other mods that would change with a mod: turning a mod off also turns off the mods that need it,
     * turning it on also turns on the mods it needs.
     *
     * @param modId the mod's id, {@code Group:Name}; case does not matter
     * @param enabled {@code true} to preview turning it on, {@code false} to preview turning it off
     * @return the other mods that {@link #setEnabled(String, boolean)} would change; empty if none or the mod is
     *     unknown
     */
    List<Mod> alsoChanged(String modId, boolean enabled);

    /**
     * Turns a mod on or off from the next start, together with the mods listed by
     * {@link #alsoChanged(String, boolean)}. Nothing changes if one of them may not be changed.
     *
     * @param modId the mod's id, {@code Group:Name}; case does not matter
     * @param enabled {@code true} to turn it on, {@code false} to turn it off
     * @return every mod that was changed, the requested one first, or an error such as {@code Mods.Unknown},
     *     {@code Mods.Forbidden} or {@code Mods.Required}
     */
    Result<List<Mod>> setEnabled(String modId, boolean enabled);

    /**
     * Lists a mod's config files, as the Mods tab shows them.
     *
     * @param modId the mod's id, {@code Group:Name}
     * @return the files with their lines; empty if there are none, the mod is unknown or hidden, or the actor is
     *     not an admin
     */
    List<ConfigFile> configFiles(String modId);

    /**
     * Lists what needs a server restart to take effect: changed {@code config.json} keys and mods turned on or
     * off.
     *
     * @return the names of the pending changes; empty if nothing waits for a restart or the actor is not an admin
     */
    List<Text> pendingRestart();

    /** What a mod is made of. */
    enum Kind {
        /** Code only. */
        PLUGIN,
        /** Assets only. */
        ASSET_PACK,
        /** Code and assets. */
        PLUGIN_WITH_ASSETS
    }

    /** A mod's state in the running server. */
    enum State {
        /** Running. */
        ENABLED,
        /** Loaded but turned off. */
        DISABLED,
        /** Failed to start. */
        FAILED,
        /** Not loaded, for example because it is turned off in the server's config. */
        NOT_LOADED
    }

    /** Whether a dependency is met. */
    enum DependencyStatus {
        /** Installed, on and in a matching version. */
        OK,
        /** Not installed. */
        MISSING,
        /** Installed in a version outside the wanted range. */
        WRONG_VERSION,
        /** Installed but turned off. */
        OFF
    }

    /**
     * A mod another mod needs.
     *
     * @param id the needed mod's id, {@code Group:Name}
     * @param range the wanted versions, for example {@code >=1.2.0}; empty for any
     * @param optional {@code true} if the mod works without it
     * @param status whether it is met
     */
    record Dependency(String id, String range, boolean optional, DependencyStatus status) {}

    /**
     * A mod or asset pack.
     *
     * @param id the mod's id, {@code Group:Name}
     * @param name the mod's name
     * @param version the mod's version; empty if unknown
     * @param description the mod's description; empty if none
     * @param authors the mod's authors
     * @param website the mod's website; empty if none
     * @param kind what the mod is made of
     * @param state the mod's state in the running server
     * @param core {@code true} for Hytale's own modules, which can't be turned off
     * @param enabledAfterRestart whether the mod will be on after the next start
     * @param serverVersion the server versions the mod asks for; empty for any
     * @param serverVersionOk whether the running server is in that range
     * @param dependencies the mods it needs (required and optional)
     * @param neededBy the names of the mods that need it and will be on after the next start
     * @param manageable whether this API may turn it on or off
     * @param hasProblems whether a required dependency is not met or the server version does not match
     */
    record Mod(
            String id,
            String name,
            String version,
            String description,
            List<String> authors,
            String website,
            Kind kind,
            State state,
            boolean core,
            boolean enabledAfterRestart,
            String serverVersion,
            boolean serverVersionOk,
            List<Dependency> dependencies,
            List<String> neededBy,
            boolean manageable,
            boolean hasProblems) {}

    /**
     * One of a mod's config files.
     *
     * @param name the file's name, relative to the mod's folder
     * @param lines the file's lines
     */
    record ConfigFile(String name, List<String> lines) {}
}
