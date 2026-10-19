package net.ofatech.controlcore.domain.model.settings;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Lists and edits registered settings as text, for UIs and commands that work with any setting. For player
 * settings pass the player; for server settings pass {@link Optional#empty()}.
 */
public interface SettingsEditor {

    /**
     * @param scope which settings to list
     * @param player whose values to show for player settings; empty shows defaults
     * @return the effective values, sorted by category then id
     */
    List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player);

    /**
     * @return the effective value, or {@code Settings.UnknownSetting}
     */
    Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player);

    /**
     * Parses and stores a value.
     *
     * @return the new snapshot, or {@code Settings.UnknownSetting}, {@code Settings.InvalidValue} or
     *     {@code Settings.WrongScope}
     */
    Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text);

    /**
     * Removes a stored value so the default applies again.
     *
     * @return the new snapshot, or {@code Settings.UnknownSetting} or {@code Settings.WrongScope}
     */
    Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player);
}
