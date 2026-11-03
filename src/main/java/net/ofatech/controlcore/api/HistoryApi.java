package net.ofatech.controlcore.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The change history: who changed which setting, key, permission, mod or player and when, as in the control
 * panel's History tab and {@code /cc-history}. Get it with {@link ControlCoreApi#history()}.
 *
 * <p>The API acts with full rights, like the console, and a rollback shows in the history as made by a mod.
 * {@link #actingFor(PlayerId)} gives the same API with a player's rights.
 *
 * @since API 2.4.0
 */
public interface HistoryApi {

    /**
     * Returns this API acting for a player, with the rights they have in the History tab: seeing it needs
     * {@code ofatech.controlcore.history} (or admin), rolling back needs {@code ofatech.controlcore.history.rollback} (or admin) and, for settings, the right to change that setting,
     * some changes (keys, permissions, bans) only admins may roll back, and only admins may clear the history. The
     * history records the player as the one who rolled a change back.
     *
     * @param player the player whose rights apply
     * @return the history API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    HistoryApi actingFor(PlayerId player);

    /**
     * Lists recorded changes, newest first.
     *
     * @param kind only changes of this kind; empty for every kind
     * @param search only changes whose name, who, target, values, source or {@code #number} contain this text
     *     (case does not matter); {@code null} or blank for all
     * @param limit the most changes to return; 0 or less for no limit
     * @return the matching changes; empty if this API may not see the history
     */
    List<Change> list(Optional<Kind> kind, String search, int limit);

    /**
     * Rolls a change back: sets the old value again, recorded as a new change.
     *
     * @param id the change's number
     * @return a confirmation message, or an error such as {@code History.Unknown}, {@code History.Forbidden},
     *     {@code History.AdminOnly} or {@code History.AlreadyRolledBack}
     */
    Result<String> rollback(long id);

    /**
     * Deletes changes older than some days.
     *
     * @param days the age in days; changes newer than this are kept
     * @return a message with the number of changes deleted, or {@code History.ClearForbidden}
     */
    Result<String> clearOlderThan(int days);

    /**
     * Deletes every recorded change.
     *
     * @return a message with the number of changes deleted, or {@code History.ClearForbidden}
     */
    Result<String> clearAll();

    /** What a change changed. */
    enum Kind {
        /** A setting registered by a mod (server setting or player preference). */
        SETTING,
        /** A {@code config.json} or world key. */
        SERVER_CONFIG,
        /** The server's keys of a key action. */
        SERVER_KEYS,
        /** A custom bind. */
        CUSTOM_BIND,
        /** A permission rule. */
        PERMISSION,
        /** A mod turned on or off. */
        MOD,
        /** A ban. */
        BAN,
        /** A player: whitelist, groups, mute, freeze or game mode. */
        PLAYER,
        /** The game action mode of a key action. */
        GAME_ACTION
    }

    /** Where a change was made. */
    enum Source {
        /** The control panel. */
        PANEL,
        /** A command. */
        COMMAND,
        /** A mod, through this API. */
        API,
        /** A rollback. */
        ROLLBACK,
        /** Restoring a snapshot or switching profile. */
        SNAPSHOT,
        /** Outside ControlCore, for example an edited file. */
        OUTSIDE,
        /** An automation rule. */
        AUTOMATION
    }

    /**
     * A recorded change.
     *
     * @param id the change's number
     * @param at when it was made
     * @param kind what it changed
     * @param target the id of what it changed, for example {@code homes:max-homes}
     * @param actor the player who made it; empty for the console or a mod
     * @param who who made it, in the reader's language ("a mod" when a mod made it)
     * @param source where it was made
     * @param what what it changed, in the reader's language
     * @param before the value before, as shown in the History tab
     * @param after the value after, as shown in the History tab
     * @param restart whether it takes effect after a restart
     * @param rollback whether it rolled another change back
     * @param rolledBack whether it was rolled back
     * @param canRollback whether this API may roll it back
     */
    record Change(
            long id,
            Instant at,
            Kind kind,
            String target,
            Optional<PlayerId> actor,
            String who,
            Source source,
            String what,
            String before,
            String after,
            boolean restart,
            boolean rollback,
            boolean rolledBack,
            boolean canRollback) {}
}
