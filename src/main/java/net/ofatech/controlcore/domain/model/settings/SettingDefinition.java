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
 * @param displayName short name shown in lists and the control panel
 * @param description one sentence explaining the setting; may be empty
 * @param category group shown in lists and the control panel; defaults to the namespace
 * @param permission who may see and change it; {@link SettingPermission#INHERIT} uses the category's rule or
 *     ControlCore's default (since API 1.4.0)
 * @param labels translation keys of the name, description, category and options (since API 1.5.0)
 * @param <T> Java type of the value
 */
public record SettingDefinition<T>(
        SettingId id,
        SettingType<T> type,
        T defaultValue,
        SettingScope scope,
        String displayName,
        String description,
        String category,
        SettingPermission permission,
        SettingLabels labels) {

    public SettingDefinition {
        Guard.notNull(id, "id");
        Guard.notNull(type, "type");
        Guard.notNull(scope, "scope");
        Guard.notBlank(displayName, "displayName");
        Guard.notNull(description, "description");
        Guard.notBlank(category, "category");
        Guard.notNull(permission, "permission");
        Guard.notNull(labels, "labels");
        Result<T> valid = type.validate(defaultValue);
        if (valid.isFailure()) {
            throw new DomainException("Default of " + id + ": " + valid.error().description());
        }
    }

    /** A definition without translation keys. */
    public SettingDefinition(
            SettingId id,
            SettingType<T> type,
            T defaultValue,
            SettingScope scope,
            String displayName,
            String description,
            String category,
            SettingPermission permission) {
        this(id, type, defaultValue, scope, displayName, description, category, permission, SettingLabels.NONE);
    }

    /** A definition that inherits who may see and change it. */
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

    /**
     * @param name short name shown to players
     * @return a copy with this display name
     */
    public SettingDefinition<T> named(String name) {
        return copy(name, description, category, permission, labels.withName(Optional.empty()));
    }

    /**
     * @param name short name shown to players, in their language
     * @return a copy with this display name
     * @since API 1.5.0
     */
    public SettingDefinition<T> named(Text name) {
        return copy(name.fallbackText(), description, category, permission, labels.withName(name.key()));
    }

    /**
     * @param text one sentence explaining the setting
     * @return a copy with this description
     */
    public SettingDefinition<T> describedAs(String text) {
        return copy(displayName, text, category, permission, labels.withDescription(Optional.empty()));
    }

    /**
     * @param text one sentence explaining the setting, in the player's language
     * @return a copy with this description
     * @since API 1.5.0
     */
    public SettingDefinition<T> describedAs(Text text) {
        return copy(displayName, text.fallbackText(), category, permission, labels.withDescription(text.key()));
    }

    /**
     * @param name group shown in lists and the control panel
     * @return a copy in this category
     */
    public SettingDefinition<T> inCategory(String name) {
        return copy(displayName, description, name, permission, labels.withCategory(Optional.empty()));
    }

    /**
     * @param name group shown in lists and the control panel, in the player's language; settings are grouped by
     *     its fallback
     * @return a copy in this category
     * @since API 1.5.0
     */
    public SettingDefinition<T> inCategory(Text name) {
        return copy(displayName, description, name.fallbackText(), permission, labels.withCategory(name.key()));
    }

    /**
     * Translates a choice's options for display (the stored value stays the option itself): option
     * {@code Server Configs} under prefix {@code homes.tabs} is the key {@code homes.tabs.server_configs}.
     *
     * @param prefix the keys' common start
     * @return a copy whose options are translated
     * @since API 1.5.0
     */
    public SettingDefinition<T> optionsKey(String prefix) {
        return copy(displayName, description, category, permission, labels.withOptions(Optional.ofNullable(prefix)));
    }

    /**
     * @param value new default
     * @return a copy with this default
     */
    public SettingDefinition<T> withDefault(T value) {
        return new SettingDefinition<>(id, type, value, scope, displayName, description, category, permission, labels);
    }

    /**
     * Who may see and change the setting: players without {@code node} do not see it.
     *
     * @param node a full permission node, or {@link SettingPermission#EVERYONE}
     * @return a copy with this rule for seeing and changing
     * @since API 1.4.0
     */
    public SettingDefinition<T> permission(String node) {
        return withPermission(SettingPermission.of(node));
    }

    /**
     * Who may see the setting; players who may see but not change it get it read-only.
     *
     * @param node a full permission node, or {@link SettingPermission#EVERYONE}
     * @since API 1.4.0
     */
    public SettingDefinition<T> viewPermission(String node) {
        return withPermission(permission.withView(node));
    }

    /**
     * Who may change the setting (they may also see it).
     *
     * @param node a full permission node, or {@link SettingPermission#EVERYONE}
     * @since API 1.4.0
     */
    public SettingDefinition<T> editPermission(String node) {
        return withPermission(permission.withEdit(node));
    }

    /** @since API 1.4.0 */
    public SettingDefinition<T> withPermission(SettingPermission rule) {
        return copy(displayName, description, category, rule, labels);
    }

    /** @return the display name, translated where the definition has a key (since API 1.5.0) */
    public Text nameText() {
        return labels.name().map(key -> Text.key(key, displayName)).orElse(Text.of(displayName));
    }

    /** @return the description, translated where the definition has a key (since API 1.5.0) */
    public Text descriptionText() {
        return labels.description().map(key -> Text.key(key, description)).orElse(Text.of(description));
    }

    /** @return the category heading, translated where the definition has a key (since API 1.5.0) */
    public Text categoryText() {
        return labels.category().map(key -> Text.key(key, category)).orElse(Text.of(category));
    }

    /** @return the default as text */
    public String formattedDefault() {
        return type.format(defaultValue);
    }

    private SettingDefinition<T> copy(
            String name, String text, String group, SettingPermission rule, SettingLabels keys) {
        return new SettingDefinition<>(id, type, defaultValue, scope, name, text, group, rule, keys);
    }

    private static <T> SettingDefinition<T> create(
            SettingId id, SettingType<T> type, T defaultValue, SettingScope scope) {
        return new SettingDefinition<>(id, type, defaultValue, scope, id.key(), "", id.namespace());
    }
}
