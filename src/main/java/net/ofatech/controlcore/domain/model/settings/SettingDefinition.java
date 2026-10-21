package net.ofatech.controlcore.domain.model.settings;

import java.util.Optional;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Describes a setting a mod registers with ControlCore.
 *
 * <pre>{@code
 * SettingDefinition<Integer> maxHomes = SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 3)
 *         .named(Text.key("homes.settings.max_homes", "Max homes"))
 *         .describedAs("How many homes each player may set")
 *         .inCategory("Homes")
 *         .editPermission("homes.moderator");
 * }</pre>
 *
 * @param id unique id, {@code namespace:key}
 * @param type kind of value and its limits
 * @param defaultValue value used until someone changes it; must be valid for {@code type}
 * @param scope server-wide or per player
 * @param nameText short name shown in lists and the control panel
 * @param descriptionText one sentence explaining the setting; may be empty
 * @param categoryText group shown in lists and the control panel; defaults to the namespace. Settings are grouped
 *     by its English text
 * @param permission who may see and change it; {@link SettingPermission#INHERIT} uses the category's rule or
 *     ControlCore's default (since API 1.4.0)
 * @param optionsKey key prefix that translates a choice's options, see {@link #optionsKey(String)} (since API 1.5.0)
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
        SettingPermission permission,
        Optional<String> optionsKey) {

    public SettingDefinition {
        Guard.notNull(id, "id");
        Guard.notNull(type, "type");
        Guard.notNull(scope, "scope");
        Guard.notNull(nameText, "nameText");
        Guard.notBlank(nameText.fallbackText(), "displayName");
        Guard.notNull(descriptionText, "descriptionText");
        Guard.notNull(categoryText, "categoryText");
        Guard.notBlank(categoryText.fallbackText(), "category");
        Guard.notNull(permission, "permission");
        optionsKey = optionsKey == null
                ? Optional.empty()
                : optionsKey.map(String::strip).filter(k -> !k.isEmpty());
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
                permission,
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
     * Starts a server-wide setting; its display name is the key and its category the namespace.
     *
     * @param id {@code namespace:key}
     * @param type kind of value
     * @param defaultValue value until an admin changes it
     * @param <T> Java type of the value
     * @return the definition
     */
    public static <T> SettingDefinition<T> server(String id, SettingType<T> type, T defaultValue) {
        return create(SettingId.of(id), type, defaultValue, SettingScope.SERVER);
    }

    /**
     * Starts a per-player setting (a preference); its display name is the key and its category the namespace.
     *
     * @param id {@code namespace:key}
     * @param type kind of value
     * @param defaultValue value for players who did not choose one
     * @param <T> Java type of the value
     * @return the definition
     */
    public static <T> SettingDefinition<T> player(String id, SettingType<T> type, T defaultValue) {
        return create(SettingId.of(id), type, defaultValue, SettingScope.PLAYER);
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
                id, type, defaultValue, scope, name, descriptionText, categoryText, permission, optionsKey);
    }

    /** @return a copy with this description (shown as it is) */
    public SettingDefinition<T> describedAs(String text) {
        return describedAs(Text.of(text));
    }

    /** @since API 1.5.0 */
    public SettingDefinition<T> describedAs(Text text) {
        return new SettingDefinition<>(
                id, type, defaultValue, scope, nameText, text, categoryText, permission, optionsKey);
    }

    /** @return a copy in this category (shown as it is) */
    public SettingDefinition<T> inCategory(String name) {
        return inCategory(Text.of(name));
    }

    /** @since API 1.5.0 */
    public SettingDefinition<T> inCategory(Text name) {
        return new SettingDefinition<>(
                id, type, defaultValue, scope, nameText, descriptionText, name, permission, optionsKey);
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
                permission,
                Optional.ofNullable(prefix));
    }

    /** @return a copy with this default */
    public SettingDefinition<T> withDefault(T value) {
        return new SettingDefinition<>(
                id, type, value, scope, nameText, descriptionText, categoryText, permission, optionsKey);
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
                id, type, defaultValue, scope, nameText, descriptionText, categoryText, rule, optionsKey);
    }

    /** @return the display name in English */
    public String displayName() {
        return nameText.fallbackText();
    }

    /** @return the description in English */
    public String description() {
        return descriptionText.fallbackText();
    }

    /** @return the category in English; settings are grouped by it */
    public String category() {
        return categoryText.fallbackText();
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
        return new SettingDefinition<>(id, type, defaultValue, scope, id.key(), "", id.namespace());
    }
}
