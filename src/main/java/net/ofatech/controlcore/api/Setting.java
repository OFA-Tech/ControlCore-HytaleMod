package net.ofatech.controlcore.api;

import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.result.Result;

/**
 * A registered setting, returned by {@link SettingsApi#register(SettingDefinition)}. Reads are cheap; keep the
 * handle and call {@link #get()} whenever you need the value instead of caching it. A stored value that is no longer
 * valid (for example after the type's range changed) reads as the default.
 *
 * @param <T> Java type of the value
 */
public interface Setting<T> {

    /**
     * Returns the current definition of this setting; a re-registration with the same type is picked up.
     *
     * @return the definition this handle was registered with
     * @throws IllegalStateException if the setting is no longer registered with this value type
     */
    SettingDefinition<T> definition();

    /**
     * Returns the server-wide value.
     *
     * @return the server value, or the default; for player settings always the default
     * @throws IllegalStateException if the setting is no longer registered with this value type
     */
    T get();

    /**
     * Returns the value that applies to a player.
     *
     * @param player a player
     * @return the player's value or the default for player settings; the server value for server settings
     * @throws IllegalStateException if the setting is no longer registered with this value type
     */
    T get(PlayerId player);

    /**
     * Changes a server setting. Saved right away; change listeners are told if the value changed.
     *
     * @param value the new value
     * @return success, or {@code Settings.InvalidValue} / {@code Settings.WrongScope} (for a player setting)
     * @throws IllegalStateException if the setting is no longer registered with this value type
     */
    Result<Void> set(T value);

    /**
     * Changes a player's preference. Change listeners are told if the value changed.
     *
     * @param player the player whose preference changes
     * @param value the new value
     * @return success, or {@code Settings.InvalidValue} / {@code Settings.WrongScope} (for a server setting)
     * @throws IllegalStateException if the setting is no longer registered with this value type
     */
    Result<Void> set(PlayerId player, T value);

    /**
     * Returns a server setting to its default.
     *
     * @return success, or {@code Settings.WrongScope} (for a player setting) / {@code Settings.UnknownSetting} (no
     *     longer registered)
     */
    Result<Void> reset();

    /**
     * Returns a player's preference to the default.
     *
     * @param player the player whose preference is reset
     * @return success, or {@code Settings.WrongScope} (for a server setting) / {@code Settings.UnknownSetting} (no
     *     longer registered)
     */
    Result<Void> reset(PlayerId player);

    /**
     * Listens to changes of this setting (by anyone: commands, the control panel or another mod) until the
     * returned subscription is closed. Listeners run synchronously on the thread that made the change.
     *
     * @param listener called with each change of this setting's value
     * @return the subscription; close it to stop listening
     */
    Subscription onChange(Consumer<? super SettingChanged> listener);
}
