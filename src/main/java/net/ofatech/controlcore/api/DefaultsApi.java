package net.ofatech.controlcore.api;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Restoring defaults for a whole section at once, like the panel's "Restore defaults" buttons and
 * {@code /cc-defaults}. Server-wide sections save a safety snapshot first when snapshots may be used. Get it with
 * {@link ControlCoreApi#defaults()}.
 *
 * <p>The API acts with full rights, like the console, and the changes show in the change history as made by a
 * mod. {@link #actingFor(PlayerId)} gives the same API with a player's rights.
 *
 * @since API 2.4.0
 */
public interface DefaultsApi {

    /**
     * Returns this API acting for a player: only the settings they may change are reset, {@link Section#SERVER_KEYS} and
     * {@link Section#PERMISSIONS} are for admins, and {@link Section#PREFERENCES} and {@link Section#MY_KEYS} reset that player's own values. The
     * change history records the player as the one who made the change.
     *
     * @param player the player whose rights apply
     * @return the defaults API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    DefaultsApi actingFor(PlayerId player);

    /**
     * Counts the values that {@link #restore(Section, Optional, String)} would reset.
     *
     * @param section the section
     * @param world for {@link Section#SERVER_CONFIGS}: only this world's keys; empty for {@code config.json}
     * @param search only values whose id, name or heading contain this text; {@code null} or blank for all
     * @return the number of values not at their default that this API may reset; 0 if it may not reset the
     *     section
     */
    int count(Section section, Optional<String> world, String search);

    /**
     * Resets every value of a section that is not at its default and that this API may change.
     *
     * @param section the section
     * @param world for {@link Section#SERVER_CONFIGS}: only this world's keys; empty for {@code config.json}
     * @param search only values whose id, name or heading contain this text; {@code null} or blank for all
     * @return a message with the number reset (and the safety snapshot's name), or an error such as
     *     {@code Defaults.Forbidden}, or {@code Commands.PlayersOnly} for a player's own section without
     *     {@link #actingFor(PlayerId)}
     */
    Result<String> restore(Section section, Optional<String> world, String search);

    /** A group of values that can be reset together. */
    enum Section {
        /** Server settings registered by mods. */
        MOD_SETTINGS,
        /** The acting player's own preferences; needs {@link DefaultsApi#actingFor(PlayerId)}. */
        PREFERENCES,
        /** The {@code config.json} keys, or one world's keys. */
        SERVER_CONFIGS,
        /** The server's key bindings. */
        SERVER_KEYS,
        /** The acting player's own key bindings; needs {@link DefaultsApi#actingFor(PlayerId)}. */
        MY_KEYS,
        /** Admins' permission rules. */
        PERMISSIONS
    }
}
