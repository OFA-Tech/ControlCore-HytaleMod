package net.ofatech.controlcore.api;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Shared settings registry. Mods register typed settings here; ControlCore stores their values, lets admins
 * and players change them (commands, control panel) and reports changes.
 *
 * <pre>{@code
 * Setting<Integer> maxHomes = api.settings()
 *         .register(SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 3).named("Max homes"))
 *         .value();
 * int limit = maxHomes.get();
 * }</pre>
 *
 * <p>The text operations ({@link #read}, {@link #write}, {@link #reset}) are for UIs and commands that work
 * with any setting. For player settings pass the player; for server settings pass {@link Optional#empty()}.
 */
public interface SettingsApi {

    /**
     * Registers a setting. Registering the same id again with the same type and scope (for example after a
     * plugin reload) replaces the definition and keeps the stored values.
     *
     * @return the handle, or {@code Settings.Conflict} if the id is registered with another type or scope
     */
    <T> Result<Setting<T>> register(SettingDefinition<T> definition);

    /** @return the definition registered under {@code id} */
    Optional<SettingDefinition<?>> definition(SettingId id);

    /** @return every registered definition, sorted by id */
    List<SettingDefinition<?>> definitions();

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
