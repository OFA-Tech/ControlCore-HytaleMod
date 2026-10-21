package net.ofatech.controlcore.domain.model.settings;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Lists and edits registered settings as text, for UIs and commands that work with any setting. For player
 * settings pass the player; for server settings pass {@link Optional#empty()}. Values are read and written in the
 * text form of their {@link SettingType} (see {@link SettingType#parse(String)} and
 * {@link SettingType#format(Object)}).
 */
public interface SettingsEditor {

    /**
     * Lists the current values of all settings of one scope.
     *
     * @param scope which settings to list
     * @param player whose values to show for player settings; empty shows defaults
     * @return the effective values in display order (for registered settings: by
     *     {@link SettingDefinition#heading()}, then id); empty if there are none
     */
    List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player);

    /**
     * Reads the current value of one setting.
     *
     * @param id the setting, for example {@code homes:max-homes}
     * @param player whose value to read for a player setting; empty for a server setting or to read the default
     * @return the effective value, or the error {@code Settings.UnknownSetting}
     */
    Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player);

    /**
     * Parses and stores a value. Listeners of {@link #onAnyChange(Consumer)} are told when the effective value
     * changes.
     *
     * @param id the setting, for example {@code homes:max-homes}
     * @param player the player whose preference to change for a player setting; empty for a server setting
     * @param text the new value as text, for example {@code 5} or {@code true}
     * @return the new snapshot, or the error {@code Settings.UnknownSetting}, {@code Settings.InvalidValue} or
     *     {@code Settings.WrongScope} (a player passed for a server setting, or none for a player setting)
     */
    Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text);

    /**
     * Removes a stored value so the default applies again.
     *
     * @param id the setting, for example {@code homes:max-homes}
     * @param player the player whose preference to reset for a player setting; empty for a server setting
     * @return the new snapshot, or the error {@code Settings.UnknownSetting} or {@code Settings.WrongScope}
     */
    Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player);

    /**
     * Listens to changes of every setting until the returned subscription is closed. Listeners run on the thread
     * that made the change.
     *
     * @param listener called with each {@link SettingChanged} event
     * @return the subscription; close it to stop listening
     * @since API 1.2.0
     */
    Subscription onAnyChange(Consumer<? super SettingChanged> listener);
}
