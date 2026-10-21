package net.ofatech.controlcore.domain.model.settings;

import java.util.Optional;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Describes a setting a mod registers with ControlCore.
 *
 * <pre>{@code
 * SettingDefinition<Integer> maxHomes = SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3)
 *         .named(Text.key("homes.settings.max_homes", "Max homes"))
 *         .describedAs("How many homes each player may set")
 *         .inCategory("Limits")          // shown under the heading "Homes: Limits"
 *         .editPermission("homes.moderator");
 * }</pre>
 *
 * @param id unique id, {@code namespace:key}
 * @param type kind of value and its limits
 * @param defaultValue value used until someone changes it; must be valid for {@code type}
 * @param scope server-wide or per player
 * @param nameText short name shown in lists and the control panel
 * @param descriptionText one sentence explaining the setting; may be empty
 * @param categoryText the mod's own group for the setting, may be empty; the heading in lists and the control
 *     panel is {@code <mod name>: <category>}, or just the mod name (see {@link #heading()})
 * @param permission who may see and change it; {@link SettingPermission#INHERIT} uses the category's rule or
 *     ControlCore's default (since API 1.4.0)
 * @param modNameText the mod's name in headings; defaults to the mod id capitalised ({@code my-mod} is
 *     {@code My Mod}), mods change it with {@code SettingsApi.setModName} (since API 2.0.0)
 * @param optionsKey key prefix that translates a choice's options, see {@link #optionsKey(String)} (since API 1.5.0)
 * @param colorSetting a colour setting shown in this text setting's row, see {@link #coloredBy(String)} (since
 *     API 2.0.0)
 * @param <T> Java type of the value
 */
public record SettingDefinition<T>(
        SettingId id,
        SettingType<T> type,
        T defaultValue,
        SettingScope scope,
        Text nameText,
        Text descriptionText,
        Text categoryText,
        Text modNameText,
        SettingPermission permission,
        Optional<String> optionsKey,
        Optional<SettingId> colorSetting) {

    /**
     * Creates a definition from all its parts; most mods start with
     * {@link #server(String, String, SettingType, Object)} or {@link #player(String, String, SettingType, Object)}
     * instead. A {@code null} {@code optionsKey} or {@code colorSetting} counts as empty, and a blank options key
     * is dropped.
     *
     * @param id unique id, {@code namespace:key}
     * @param type kind of value and its limits
     * @param defaultValue value used until someone changes it
     * @param scope server-wide or per player
     * @param nameText short name; its English text must not be blank
     * @param descriptionText one sentence explaining the setting; may be empty
     * @param categoryText the mod's own group for the setting; may be empty
     * @param modNameText the mod's name in headings
     * @param permission who may see and change it
     * @param optionsKey key prefix that translates a choice's options
     * @param colorSetting a colour setting shown in this text setting's row
     * @throws DomainException if a required part is {@code null}, the name is blank, or {@code defaultValue} is not
     *     valid for {@code type}
     */
    public SettingDefinition {
        Guard.notNull(id, "id");
        Guard.notNull(type, "type");
        Guard.notNull(scope, "scope");
        Guard.notNull(nameText, "nameText");
        Guard.notBlank(nameText.fallbackText(), "displayName");
        Guard.notNull(descriptionText, "descriptionText");
        Guard.notNull(categoryText, "categoryText");
        Guard.notNull(modNameText, "modNameText");
        Guard.notNull(permission, "permission");
        optionsKey = optionsKey == null
                ? Optional.empty()
                : optionsKey.map(String::strip).filter(k -> !k.isEmpty());
        colorSetting = colorSetting == null ? Optional.empty() : colorSetting;
        Result<T> valid = type.validate(defaultValue);
        if (valid.isFailure()) {
            throw new DomainException("Default of " + id + ": " + valid.error().description());
        }
    }

    /**
     * Creates a definition with fixed (untranslated) texts; the mod's name in headings is
     * {@link #defaultModName(String) derived from} the id's namespace.
     *
     * @param id unique id, {@code namespace:key}
     * @param type kind of value and its limits
     * @param defaultValue value used until someone changes it; must be valid for {@code type}
     * @param scope server-wide or per player
     * @param displayName short name shown in lists and the control panel; not blank
     * @param description one sentence explaining the setting; may be empty
     * @param category the mod's own group for the setting; may be empty
     * @param permission who may see and change it, or {@link SettingPermission#INHERIT}
     * @throws DomainException if a required part is {@code null}, the name is blank, or {@code defaultValue} is not
     *     valid for {@code type}
     */
    public SettingDefinition(
            SettingId id,
            SettingType<T> type,
            T defaultValue,
            SettingScope scope,
            String displayName,
            String description,
            String category,
            SettingPermission permission) {
        this(
                id,
                type,
                defaultValue,
                scope,
                Text.of(displayName),
                Text.of(description),
                Text.of(category),
                Text.of(defaultModName(id.namespace())),
                permission,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Creates a definition with fixed texts that inherits who may see and change it
     * ({@link SettingPermission#INHERIT}).
     *
     * @param id unique id, {@code namespace:key}
     * @param type kind of value and its limits
     * @param defaultValue value used until someone changes it; must be valid for {@code type}
     * @param scope server-wide or per player
     * @param displayName short name shown in lists and the control panel; not blank
     * @param description one sentence explaining the setting; may be empty
     * @param category the mod's own group for the setting; may be empty
     * @throws DomainException if a required part is {@code null}, the name is blank, or {@code defaultValue} is not
     *     valid for {@code type}
     */
    public SettingDefinition(
            SettingId id,
            SettingType<T> type,
            T defaultValue,
            SettingScope scope,
            String displayName,
            String description,
            String category) {
        this(id, type, defaultValue, scope, displayName, description, category, SettingPermission.INHERIT);
    }

    /**
     * Starts a server-wide setting with the id {@code modId:key}; its display name is the key, it has no category
     * and its heading is the mod's name.
     *
     * @param modId your mod's id, lower case ({@code [a-z0-9_-]}); also the first part of the setting id
     * @param key the setting within your mod, lower case ({@code [a-z0-9_.-]}), for example {@code max-homes}
     * @param type kind of value
     * @param defaultValue value until an admin changes it; must be valid for {@code type}
     * @param <T> Java type of the value
     * @return the definition
     * @throws DomainException if {@code modId} or {@code key} has the wrong format, or {@code defaultValue} is not
     *     valid for {@code type}
     * @since API 2.0.0
     */
    public static <T> SettingDefinition<T> server(String modId, String key, SettingType<T> type, T defaultValue) {
        return create(new SettingId(modId, key), type, defaultValue, SettingScope.SERVER);
    }

    /**
     * Starts a per-player setting (a preference) with the id {@code modId:key}; its display name is the key, it has
     * no category and its heading is the mod's name. See {@link #server(String, String, SettingType, Object)}.
     *
     * @param modId your mod's id, lower case ({@code [a-z0-9_-]}); also the first part of the setting id
     * @param key the setting within your mod, lower case ({@code [a-z0-9_.-]}), for example {@code show-hud}
     * @param type kind of value
     * @param defaultValue value for players who did not choose one; must be valid for {@code type}
     * @param <T> Java type of the value
     * @return the definition
     * @throws DomainException if {@code modId} or {@code key} has the wrong format, or {@code defaultValue} is not
     *     valid for {@code type}
     * @since API 2.0.0
     */
    public static <T> SettingDefinition<T> player(String modId, String key, SettingType<T> type, T defaultValue) {
        return create(new SettingId(modId, key), type, defaultValue, SettingScope.PLAYER);
    }

    /**
     * Sets the display name, shown as it is (not translated).
     *
     * @param name short name, for example {@code Max homes}; not blank
     * @return a copy with this display name
     * @throws DomainException if {@code name} is blank
     */
    public SettingDefinition<T> named(String name) {
        return named(Text.of(name));
    }

    /**
     * Sets the display name as translatable text.
     *
     * @param name short name, for example {@code Text.key("homes.settings.max_homes", "Max homes")}; its English
     *     text must not be blank
     * @return a copy with this display name
     * @throws DomainException if {@code name} is {@code null} or its English text is blank
     * @since API 1.5.0
     */
    public SettingDefinition<T> named(Text name) {
        return new SettingDefinition<>(
                id,
                type,
                defaultValue,
                scope,
                name,
                descriptionText,
                categoryText,
                modNameText,
                permission,
                optionsKey,
                colorSetting);
    }

    /**
     * Sets the description, shown as it is (not translated).
     *
     * @param text one sentence explaining the setting; may be empty
     * @return a copy with this description
     */
    public SettingDefinition<T> describedAs(String text) {
        return describedAs(Text.of(text));
    }

    /**
     * Sets the description as translatable text.
     *
     * @param text one sentence explaining the setting; may be empty
     * @return a copy with this description
     * @throws DomainException if {@code text} is {@code null}
     * @since API 1.5.0
     */
    public SettingDefinition<T> describedAs(Text text) {
        return new SettingDefinition<>(
                id,
                type,
                defaultValue,
                scope,
                nameText,
                text,
                categoryText,
                modNameText,
                permission,
                optionsKey,
                colorSetting);
    }

    /**
     * Puts the setting in one of the mod's own groups, shown as it is (not translated); the heading becomes
     * {@code <mod name>: <category>}.
     *
     * @param name the category, for example {@code Limits}; empty for none
     * @return a copy in this category
     */
    public SettingDefinition<T> inCategory(String name) {
        return inCategory(Text.of(name));
    }

    /**
     * Puts the setting in one of the mod's own groups, as translatable text; the heading becomes
     * {@code <mod name>: <category>}.
     *
     * @param name the category; empty for none
     * @return a copy in this category
     * @throws DomainException if {@code name} is {@code null}
     * @since API 1.5.0
     */
    public SettingDefinition<T> inCategory(Text name) {
        return new SettingDefinition<>(
                id,
                type,
                defaultValue,
                scope,
                nameText,
                descriptionText,
                name,
                modNameText,
                permission,
                optionsKey,
                colorSetting);
    }

    /**
     * Translates a choice's options for display (the stored value stays the option itself): option
     * {@code Server Configs} under prefix {@code homes.tabs} is the key {@code homes.tabs.server_configs}. See
     * {@link #optionText(String)}.
     *
     * @param prefix translation key prefix; {@code null} or blank turns translation off
     * @return a copy that translates its options with this prefix
     * @since API 1.5.0
     */
    public SettingDefinition<T> optionsKey(String prefix) {
        return new SettingDefinition<>(
                id,
                type,
                defaultValue,
                scope,
                nameText,
                descriptionText,
                categoryText,
                modNameText,
                permission,
                Optional.ofNullable(prefix),
                colorSetting);
    }

    /**
     * Sets the default value.
     *
     * @param value value used until someone changes it; must be valid for the type
     * @return a copy with this default
     * @throws DomainException if {@code value} is not valid for the type
     */
    public SettingDefinition<T> withDefault(T value) {
        return new SettingDefinition<>(
                id,
                type,
                value,
                scope,
                nameText,
                descriptionText,
                categoryText,
                modNameText,
                permission,
                optionsKey,
                colorSetting);
    }

    /**
     * Sets who may see and change the setting: players without {@code node} do not see it.
     *
     * @param node a full permission node, for example {@code homes.moderator}, or {@link SettingPermission#EVERYONE}
     * @return a copy with this rule for both seeing and changing
     * @throws DomainException if the node contains spaces
     * @since API 1.4.0
     */
    public SettingDefinition<T> permission(String node) {
        return withPermission(SettingPermission.of(node));
    }

    /**
     * Sets who may see the setting; players who may see but not change it get it read-only. Who may change it
     * stays as it was.
     *
     * @param node a full permission node, {@link SettingPermission#EVERYONE}, or {@code null} to inherit
     * @return a copy with this view rule
     * @throws DomainException if the node contains spaces
     * @since API 1.4.0
     */
    public SettingDefinition<T> viewPermission(String node) {
        return withPermission(permission.withView(node));
    }

    /**
     * Sets who may change the setting (they may also see it). Who may see it stays as it was.
     *
     * @param node a full permission node, {@link SettingPermission#EVERYONE}, or {@code null} to inherit
     * @return a copy with this edit rule
     * @throws DomainException if the node contains spaces
     * @since API 1.4.0
     */
    public SettingDefinition<T> editPermission(String node) {
        return withPermission(permission.withEdit(node));
    }

    /**
     * Replaces the whole rule for who may see and change the setting.
     *
     * @param rule the rule; {@link SettingPermission#INHERIT} leaves it to the category or ControlCore's default
     * @return a copy with this rule
     * @throws DomainException if {@code rule} is {@code null}
     * @since API 1.4.0
     */
    public SettingDefinition<T> withPermission(SettingPermission rule) {
        return new SettingDefinition<>(
                id,
                type,
                defaultValue,
                scope,
                nameText,
                descriptionText,
                categoryText,
                modNameText,
                rule,
                optionsKey,
                colorSetting);
    }

    /**
     * Shows a colour setting in this text setting's row: one line with a colour picker next to the text field, and
     * the colour setting is not listed on its own. Use it for a message and the colour it is sent in.
     *
     * @param colorSettingId id of a {@link SettingType#color()} setting with the same scope, for example
     *     {@code homes:welcome-color}
     * @return a copy shown together with that colour setting
     * @throws DomainException if {@code colorSettingId} is not a valid setting id
     * @since API 2.0.0
     */
    public SettingDefinition<T> coloredBy(String colorSettingId) {
        return new SettingDefinition<>(
                id,
                type,
                defaultValue,
                scope,
                nameText,
                descriptionText,
                categoryText,
                modNameText,
                permission,
                optionsKey,
                Optional.of(SettingId.of(colorSettingId)));
    }

    /**
     * Returns the display name in English, the fallback text of {@link #nameText()}.
     *
     * @return the display name, never blank
     */
    public String displayName() {
        return nameText.fallbackText();
    }

    /**
     * Returns the description in English, the fallback text of {@link #descriptionText()}.
     *
     * @return the description; empty when there is none
     */
    public String description() {
        return descriptionText.fallbackText();
    }

    /**
     * Returns the mod's own category in English, without surrounding spaces.
     *
     * @return the category; empty when it has none
     */
    public String category() {
        return categoryText.fallbackText().strip();
    }

    /**
     * Returns the mod's name in English as shown in headings, without surrounding spaces.
     *
     * @return the mod's name, for example {@code My Mod}; may be empty
     */
    public String modName() {
        return modNameText.fallbackText().strip();
    }

    /**
     * Returns the heading the setting is listed under, in English; settings are grouped and sorted by it.
     *
     * @return {@code <mod name>: <category>}, or just one of them when the other is empty
     * @since API 2.0.0
     */
    public String heading() {
        return heading(modName(), category());
    }

    /**
     * Returns the heading the setting is listed under, in the viewer's language.
     *
     * @param translator translates the mod's name and the category for the viewer
     * @return {@code <mod name>: <category>}, or just one of them when the other is empty
     * @since API 2.0.0
     */
    public String heading(Translator translator) {
        return heading(
                translator.translate(modNameText).strip(),
                translator.translate(categoryText).strip());
    }

    /**
     * Sets the mod's name shown in headings. ControlCore sets it when the setting is registered; mods change it
     * with {@code SettingsApi.setModName}.
     *
     * @param name the mod's name; empty leaves only the category in headings
     * @return a copy whose headings show {@code name} as the mod's name
     * @throws DomainException if {@code name} is {@code null}
     * @since API 2.0.0
     */
    public SettingDefinition<T> withModName(Text name) {
        return new SettingDefinition<>(
                id,
                type,
                defaultValue,
                scope,
                nameText,
                descriptionText,
                categoryText,
                name,
                permission,
                optionsKey,
                colorSetting);
    }

    /**
     * Turns a mod id into the name used in headings until the mod sets its own: parts split at {@code -} and
     * {@code _} are capitalised and joined with spaces.
     *
     * @param modId the mod's id, for example {@code my-mod}
     * @return the name: {@code my-mod} and {@code my_mod} become {@code My Mod}
     */
    public static String defaultModName(String modId) {
        StringBuilder name = new StringBuilder();
        for (String part : modId.split("[-_]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private static String heading(String mod, String category) {
        if (mod.isEmpty()) {
            return category;
        }
        return category.isEmpty() ? mod : mod + ": " + category;
    }

    /**
     * Returns how one of a choice's options is shown to players.
     *
     * @param option one of the choice's options, for example {@code Server Configs}
     * @return the translatable text under the {@link #optionsKey(String) options key} with the option as English
     *     fallback, or the option as it is when there is no options key
     */
    public Text optionText(String option) {
        return optionsKey
                .map(prefix -> Text.key(prefix + "." + Text.slug(option), option))
                .orElse(Text.of(option));
    }

    /**
     * Writes the default value as text.
     *
     * @return the default in the form {@link SettingType#format(Object)} writes, for example {@code 3}
     */
    public String formattedDefault() {
        return type.format(defaultValue);
    }

    private static <T> SettingDefinition<T> create(
            SettingId id, SettingType<T> type, T defaultValue, SettingScope scope) {
        return new SettingDefinition<>(id, type, defaultValue, scope, id.key(), "", "");
    }
}
