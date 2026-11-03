package net.ofatech.controlcore.api;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Shared settings registry. Mods register typed settings here; ControlCore stores their values, lets admins
 * and players change them (commands, control panel) and reports changes. Get it with
 * {@link ControlCoreApi#settings()}.
 *
 * <pre>{@code
 * Setting<Integer> maxHomes = api.settings()
 *         .register(SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3).named("Max homes"))
 *         .value();
 * int limit = maxHomes.get();
 * }</pre>
 *
 * <p>The text operations inherited from {@link SettingsEditor} ({@code snapshot}, {@code read}, {@code write},
 * {@code reset}) are for UIs and commands that work with any setting. Since API 2.4.0 {@code read}, {@code write}
 * and {@code reset} also take the server's {@code config.json} keys ({@code server:<key>}) and world keys
 * ({@code world:<world>.<key>}), listed by {@link #serverConfigs()}.
 *
 * <p>The API acts with full rights, like the console, and the change history shows the change as made by a mod.
 * {@link #actingFor(PlayerId)} gives the same API with the rights a player has in the control panel.
 */
public interface SettingsApi extends SettingsEditor {

    /**
     * Registers a setting. Registering the same id again with the same type and scope (for example after a
     * plugin reload) replaces the definition and keeps the stored values.
     *
     * @param <T> Java type of the setting's value
     * @param definition the setting to register
     * @return the handle to read and change the value, or {@code Settings.Conflict} if the id is registered with
     *     another type or scope
     * @throws NullPointerException if {@code definition} is {@code null}
     */
    <T> Result<Setting<T>> register(SettingDefinition<T> definition);

    /**
     * Finds a registered setting's definition.
     *
     * @param id the setting's id, for example {@code homes:max-homes}
     * @return the definition registered under {@code id}, or empty if there is none
     */
    Optional<SettingDefinition<?>> definition(SettingId id);

    /**
     * Lists every registered setting, server settings and player preferences.
     *
     * @return every registered definition, sorted by id
     */
    List<SettingDefinition<?>> definitions();

    /**
     * Sets the name a mod's settings and keys are shown under ({@code <name>: <category>}). Without it the mod id
     * is capitalised: {@code my-mod} is shown as {@code My Mod}.
     *
     * @param modId the mod id, the first part of its setting and key ids
     * @param name the name, for example {@code Text.key("homes.mod_name", "Home Teleports")}; {@code null} goes back
     *     to the capitalised id
     * @throws NullPointerException if {@code modId} is {@code null}
     * @since API 2.0.0
     */
    void setModName(String modId, Text name);

    /**
     * Sets the name a mod's settings and keys are shown under, in one language for everyone.
     *
     * @param modId the mod id, the first part of its setting and key ids
     * @param name the name, for example {@code "Home Teleports"}; {@code null} goes back to the capitalised id
     * @throws NullPointerException if {@code modId} is {@code null}
     * @see #setModName(String, Text)
     */
    default void setModName(String modId, String name) {
        setModName(modId, name == null ? null : Text.of(name));
    }

    /**
     * Returns the name a mod's settings and keys are shown under.
     *
     * @param modId the mod id
     * @return the name {@code modId}'s settings and keys are shown under: the one set with
     *     {@link #setModName(String, Text)}, else the capitalised id
     * @since API 2.0.0
     */
    Text modName(String modId);

    /**
     * Sets who may see and change every setting in a category that has no rule of its own (see
     * {@link SettingDefinition#permission(String)}). Admins can still override it.
     *
     * @param category the category, as given to {@link SettingDefinition#inCategory}; case does not matter
     * @param rule the rule; {@link SettingPermission#INHERIT} removes it
     * @throws NullPointerException if {@code category} or {@code rule} is {@code null}
     * @since API 1.4.0
     */
    void setCategoryPermission(String category, SettingPermission rule);

    /**
     * Returns the rule a mod set for a category with {@link #setCategoryPermission(String, SettingPermission)}.
     *
     * @param category the category; case does not matter
     * @return the rule a mod set for {@code category}, or {@link SettingPermission#INHERIT} if none (or
     *     {@code category} is {@code null})
     * @since API 1.4.0
     */
    SettingPermission categoryPermission(String category);

    /**
     * Returns this API acting for a player. {@code snapshot}, {@code read}, {@code write}, {@code reset} and
     * {@link #serverConfigs()} then follow the player's rights in the control panel: settings they may not see are
     * left out or reported as unknown, settings they may only see are read-only, preferences of other players are
     * refused ({@code Settings.Forbidden}), and the change history records the player as the one who made the
     * change. Registering settings, mod names and category rules are not restricted: they describe the calling
     * mod's own settings.
     *
     * @param player the player whose rights apply
     * @return the settings API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     * @since API 2.4.0
     */
    SettingsApi actingFor(PlayerId player);

    /**
     * Lists the server's {@code config.json} keys and every world's keys, as settings.
     *
     * @return the keys this API may see: ids {@code server:<key>} for {@code config.json} and
     *     {@code world:<world>.<key>} for worlds, server keys first
     * @since API 2.4.0
     */
    List<SettingSnapshot> serverConfigs();

    /**
     * Lists the loaded worlds whose keys {@link #serverConfigs()} lists.
     *
     * @return the world names, as Hytale reports them
     * @since API 2.4.0
     */
    List<String> worlds();
}
