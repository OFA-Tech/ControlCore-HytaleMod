package net.ofatech.controlcore.api;

import java.util.List;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Profiles: snapshots marked as ready-made server setups to switch between, plus ControlCore's built-in presets, as in the
 * control panel's Profiles tab and {@code /cc-profiles}. Get it with {@link ControlCoreApi#profiles()}.
 *
 * <p>The API acts with full rights, like the console, and a switch shows in the change history as made by a mod.
 * {@link #actingFor(PlayerId)} gives the same API with a player's rights.
 *
 * @since API 2.4.0
 */
public interface ProfilesApi {

    /**
     * Returns this API acting for a player, with the rights they have in the Profiles tab: it needs
     * {@code ofatech.controlcore.snapshots} (or admin), and only admins may unmark profiles or switch the parts only
     * admins may restore. The change history records the player as the one who switched.
     *
     * @param player the player whose rights apply
     * @return the profiles API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    ProfilesApi actingFor(PlayerId player);

    /**
     * Lists the profiles, the built-in presets first.
     *
     * @return every profile; empty if this API may not use snapshots
     */
    List<Profile> list();

    /**
     * Describes the active profile and whether the settings still match it.
     *
     * @return the status line the Profiles tab shows, in the reader's language
     */
    String status();

    /**
     * Compares a profile with the current settings without changing anything.
     *
     * @param id the profile's id, as in {@link Profile#id()}
     * @return what switching would change, or an error such as {@code Snapshots.Unknown} or
     *     {@code Snapshots.Forbidden}
     */
    Result<SnapshotsApi.Preview> preview(String id);

    /**
     * Switches to a profile: saves a safety snapshot of what it changes, applies it and marks it active.
     *
     * @param id the profile's id, as in {@link Profile#id()}
     * @return a message with what changed, or an error such as {@code Snapshots.Unknown} or
     *     {@code Snapshots.Forbidden}
     */
    Result<String> switchTo(String id);

    /**
     * Marks a snapshot as a profile.
     *
     * @param name the snapshot's name
     * @return a confirmation message, or an error such as {@code Snapshots.Unknown}, {@code Snapshots.AdminParts}
     *     or {@code Snapshots.Forbidden}
     */
    Result<String> mark(String name);

    /**
     * Turns a profile back into a plain snapshot.
     *
     * @param id the profile's id
     * @return a confirmation message, or an error such as {@code Snapshots.Unknown},
     *     {@code Snapshots.ProfileReplace} or {@code Snapshots.Forbidden}
     */
    Result<String> unmark(String id);

    /**
     * A profile.
     *
     * @param id the id to pass to {@link #switchTo(String)}
     * @param name the profile's name, in the reader's language
     * @param details what it holds and when it was saved, in the reader's language
     * @param builtIn whether it is one of ControlCore's built-in presets
     * @param active whether it is the active profile
     */
    record Profile(String id, String name, String details, boolean builtIn, boolean active) {}
}
