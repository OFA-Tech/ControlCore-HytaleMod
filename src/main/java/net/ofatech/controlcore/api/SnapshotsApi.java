package net.ofatech.controlcore.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Snapshots: saved copies of the server's settings that can be compared and restored, as in the control panel's
 * Snapshots tab and {@code /cc-snapshot}. Get it with {@link ControlCoreApi#snapshots()}.
 *
 * <p>The API acts with full rights, like the console, and a restore shows in the change history as made by a mod.
 * {@link #actingFor(PlayerId)} gives the same API with a player's rights.
 *
 * @since API 2.4.0
 */
public interface SnapshotsApi {

    /**
     * Returns this API acting for a player, with the rights they have in the Snapshots tab: it needs
     * {@code ofatech.controlcore.snapshots} (or admin), only admins may save or restore the {@link Part#adminOnly()}
     * parts, and only admins may replace, delete or (un)mark snapshots that hold them or are profiles
     * ({@code Snapshots.AdminParts}, {@code Snapshots.ProfileReplace}). The change history records the player as the
     * one who restored a snapshot.
     *
     * @param player the player whose rights apply
     * @return the snapshots API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    SnapshotsApi actingFor(PlayerId player);

    /**
     * Lists the saved snapshots, profiles included.
     *
     * @return every snapshot, in the order the Snapshots tab shows them; empty if this API may not use snapshots
     */
    List<Snapshot> list();

    /**
     * Saves the current settings as a snapshot, replacing one with the same name.
     *
     * @param name the name: 1 to 40 letters, digits, {@code -} or {@code _}
     * @param parts what to save; at least one
     * @param mod only this mod's settings ({@code Group:Name}); empty for every mod
     * @return a confirmation message, or an error such as {@code Snapshots.InvalidName}, {@code Snapshots.NoParts},
     *     {@code Snapshots.AdminParts} or {@code Snapshots.Forbidden}
     */
    Result<String> save(String name, Set<Part> parts, Optional<String> mod);

    /**
     * Compares a snapshot with the current settings without changing anything.
     *
     * @param name the snapshot's name
     * @return what restoring it would change, or an error such as {@code Snapshots.Unknown} or
     *     {@code Snapshots.Forbidden}
     */
    Result<Preview> preview(String name);

    /**
     * Restores a snapshot: sets every saved value this API may change.
     *
     * @param name the snapshot's name
     * @return a message with what changed, or an error such as {@code Snapshots.Unknown} or
     *     {@code Snapshots.Forbidden}
     */
    Result<String> restore(String name);

    /**
     * Saves the current settings of the snapshot's parts as a safety snapshot first, then restores it.
     *
     * @param name the snapshot's name
     * @return a message with what changed and the safety snapshot's name, or an error such as
     *     {@code Snapshots.Unknown} or {@code Snapshots.Forbidden}
     */
    Result<String> restoreSafe(String name);

    /**
     * Deletes a snapshot.
     *
     * @param name the snapshot's name
     * @return a confirmation message, or an error such as {@code Snapshots.Unknown}, {@code Snapshots.AdminParts},
     *     {@code Snapshots.ProfileReplace} or {@code Snapshots.Forbidden}
     */
    Result<String> delete(String name);

    /**
     * Deletes every snapshot this API may delete. Profiles are kept.
     *
     * @return a message with the number deleted and kept, or {@code Snapshots.Forbidden}
     */
    Result<String> deleteAll();

    /** A part of the server's settings a snapshot can hold. */
    enum Part {
        /** The {@code config.json} keys. */
        CONFIG,
        /** The worlds' keys. */
        WORLDS,
        /** Settings registered by mods. */
        SETTINGS,
        /** The server's key bindings and custom binds; admins only. */
        KEYS,
        /** Permission rules; admins only. */
        PERMISSIONS,
        /** Which mods are on; admins only. */
        MODS,
        /** Players' whitelist, groups, mutes and freezes. */
        PLAYERS;

        /**
         * Tells whether only admins may save and restore this part.
         *
         * @return {@code true} for {@link #KEYS}, {@link #PERMISSIONS} and {@link #MODS}
         */
        public boolean adminOnly() {
            return this == KEYS || this == PERMISSIONS || this == MODS;
        }
    }

    /**
     * A saved snapshot.
     *
     * @param name the snapshot's name
     * @param createdAt when it was saved
     * @param createdBy who saved it; empty if unknown
     * @param parts what it holds
     * @param mods the mods it is limited to; empty for every mod
     * @param profile whether it is a profile (see {@link ProfilesApi})
     * @param locked whether this API may not replace, delete or (un)mark it
     */
    record Snapshot(
            String name,
            Instant createdAt,
            String createdBy,
            Set<Part> parts,
            Set<String> mods,
            boolean profile,
            boolean locked) {}

    /**
     * What restoring a snapshot or switching to a profile would change.
     *
     * @param name the snapshot's or profile's name
     * @param lines one line per change, in the reader's language
     * @param changes the number of values that would change
     * @param skipped the number of saved values that no longer exist and would be skipped
     * @param adminOnly the number of changes only an admin may make, left out for others
     */
    record Preview(String name, List<String> lines, int changes, int skipped, int adminOnly) {}
}
