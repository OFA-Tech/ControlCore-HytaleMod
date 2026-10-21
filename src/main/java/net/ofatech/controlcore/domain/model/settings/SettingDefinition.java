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

    /** A definition with fixed (untranslated) texts. */
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

    /** A definition with fixed texts that inherits who may see and change it. */
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
     * @param defaultValue value until an admin changes it
     * @param <T> Java type of the value
     * @return the definition
     * @since API 2.0.0
     */
    public static <T> SettingDefinition<T> server(String modId, String key, SettingType<T> type, T defaultValue) {
        return create(new SettingId(modId, key), type, defaultValue, SettingScope.SERVER);
    }

    /**
     * Starts a per-player setting (a preference) with the id {@code modId:key}; see
     * {@link #server(String, String, SettingType, Object)}.
     *
     * @param defaultValue value for players who did not choose one
     * @since API 2.0.0
     */
    public static <T> SettingDefinition<T> player(String modId, String key, SettingType<T> type, T defaultValue) {
        return create(new SettingId(modId, key), type, defaultValue, SettingScope.PLAYER);
    }

    /** @return a copy with this display name (shown as it is) */
    public SettingDefinition<T> named(String name) {
        return named(Text.of(name));
    }

    /**
     * @param name short name, for example {@code Text.key("homes.settings.max_homes", "Max homes")}
     * @return a copy with this display name
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

    /** @return a copy with this description (shown as it is) */
    public SettingDefinition<T> describedAs(String text) {
        return describedAs(Text.of(text));
    }

    /** @since API 1.5.0 */
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

    /** @return a copy in this category (shown as it is) */
    public SettingDefinition<T> inCategory(String name) {
        return inCategory(Text.of(name));
    }

    /** @since API 1.5.0 */
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
     * {@code Server Configs} under prefix {@code homes.tabs} is the key {@code homes.tabs.server_configs}.
     *
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

    /** @return a copy with this default */
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
     * Who may see and change the setting: players without {@code node} do not see it.
     *
     * @param node a full permission node, or {@link SettingPermission#EVERYONE}
     * @since API 1.4.0
     */
    public SettingDefinition<T> permission(String node) {
        return withPermission(SettingPermission.of(node));
    }

    /**
     * Who may see the setting; players who may see but not change it get it read-only.
     *
     * @since API 1.4.0
     */
    public SettingDefinition<T> viewPermission(String node) {
        return withPermission(permission.withView(node));
    }

    /**
     * Who may change the setting (they may also see it).
     *
     * @since API 1.4.0
     */
    public SettingDefinition<T> editPermission(String node) {
        return withPermission(permission.withEdit(node));
    }

    /** @since API 1.4.0 */
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

    /** @return the display name in English */
    public String displayName() {
        return nameText.fallbackText();
    }

    /** @return the description in English */
    public String description() {
        return descriptionText.fallbackText();
    }

    /** @return the mod's own category in English; empty when it has none */
    public String category() {
        return categoryText.fallbackText().strip();
    }

    /** @return the mod's name in English */
    public String modName() {
        return modNameText.fallbackText().strip();
    }

    /**
     * @return the heading in English: {@code <mod name>: <category>}, just one of them when the other is empty;
     *     settings are grouped and sorted by it
     * @since API 2.0.0
     */
    public String heading() {
        return heading(modName(), category());
    }

    /**
     * @return the heading in the viewer's language
     * @since API 2.0.0
     */
    public String heading(Translator translator) {
        return heading(
                translator.translate(modNameText).strip(),
                translator.translate(categoryText).strip());
    }

    /**
     * @return a copy whose headings show {@code name} as the mod's name; an empty name leaves only the category
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

    /** @return {@code modId} as a name: {@code my-mod} and {@code my_mod} become {@code My Mod} */
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

    /** @return how {@code option}, one of a choice's options, is shown */
    public Text optionText(String option) {
        return optionsKey
                .map(prefix -> Text.key(prefix + "." + Text.slug(option), option))
                .orElse(Text.of(option));
    }

    /** @return the default as text */
    public String formattedDefault() {
        return type.format(defaultValue);
    }

    private static <T> SettingDefinition<T> create(
            SettingId id, SettingType<T> type, T defaultValue, SettingScope scope) {
        return new SettingDefinition<>(id, type, defaultValue, scope, id.key(), "", "");
    }
}
