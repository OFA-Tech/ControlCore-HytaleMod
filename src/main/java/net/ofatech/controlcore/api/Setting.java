package net.ofatech.controlcore.api;

import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.result.Result;

/**
 * A registered setting, returned by {@link SettingsApi#register(SettingDefinition)}. Reads are cheap; keep the
 * handle and call {@link #get()} whenever you need the value instead of caching it.
 *
 * @param <T> Java type of the value
 */
public interface Setting<T> {

    /** @return the definition this handle was registered with */
    SettingDefinition<T> definition();

    /** @return the server value, or the default; for player settings always the default */
    T get();

    /**
     * @param player a player
     * @return the player's value or the default for player settings; the server value for server settings
     */
    T get(PlayerId player);

    /**
     * Changes a server setting.
     *
     * @return success, or {@code Settings.InvalidValue} / {@code Settings.WrongScope}
     */
    Result<Void> set(T value);

    /**
     * Changes a player's preference.
     *
     * @return success, or {@code Settings.InvalidValue} / {@code Settings.WrongScope}
     */
    Result<Void> set(PlayerId player, T value);

    /**
     * Returns a server setting to its default.
     *
     * @return success, or {@code Settings.WrongScope}
     */
    Result<Void> reset();

    /**
     * Returns a player's preference to the default.
     *
     * @return success, or {@code Settings.WrongScope}
     */
    Result<Void> reset(PlayerId player);

    /**
     * Listens to changes of this setting (by anyone: commands, the control panel or another mod) until the
     * returned subscription is closed. Listeners run synchronously on the thread that made the change.
     */
    Subscription onChange(Consumer<? super SettingChanged> listener);
}
