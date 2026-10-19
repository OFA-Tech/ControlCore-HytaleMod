package net.ofatech.controlcore.api;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
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
 * <p>The text operations inherited from {@link SettingsEditor} ({@code snapshot}, {@code read}, {@code write},
 * {@code reset}) are for UIs and commands that work with any setting.
 */
public interface SettingsApi extends SettingsEditor {

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
}
