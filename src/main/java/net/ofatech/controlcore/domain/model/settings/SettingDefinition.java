package net.ofatech.controlcore.domain.model.settings;

import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Describes a setting a mod registers with ControlCore.
 *
 * <pre>{@code
 * SettingDefinition<Integer> maxHomes = SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 3)
 *         .named("Max homes")
 *         .describedAs("How many homes each player may set")
 *         .inCategory("Homes");
 * }</pre>
 *
 * @param id unique id, {@code namespace:key}
 * @param type kind of value and its limits
 * @param defaultValue value used until someone changes it; must be valid for {@code type}
 * @param scope server-wide or per player
 * @param displayName short name shown in lists and the control panel
 * @param description one sentence explaining the setting; may be empty
 * @param category group shown in lists and the control panel; defaults to the namespace
 * @param <T> Java type of the value
 */
public record SettingDefinition<T>(
        SettingId id,
        SettingType<T> type,
        T defaultValue,
        SettingScope scope,
        String displayName,
        String description,
        String category) {

    public SettingDefinition {
        Guard.notNull(id, "id");
        Guard.notNull(type, "type");
        Guard.notNull(scope, "scope");
        Guard.notBlank(displayName, "displayName");
        Guard.notNull(description, "description");
        Guard.notBlank(category, "category");
        Result<T> valid = type.validate(defaultValue);
        if (valid.isFailure()) {
            throw new DomainException("Default of " + id + ": " + valid.error().description());
        }
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
        return new SettingDefinition<>(id, type, defaultValue, scope, name, description, category);
    }

    /**
     * @param text one sentence explaining the setting
     * @return a copy with this description
     */
    public SettingDefinition<T> describedAs(String text) {
        return new SettingDefinition<>(id, type, defaultValue, scope, displayName, text, category);
    }

    /**
     * @param name group shown in lists and the control panel
     * @return a copy in this category
     */
    public SettingDefinition<T> inCategory(String name) {
        return new SettingDefinition<>(id, type, defaultValue, scope, displayName, description, name);
    }

    /**
     * @param value new default
     * @return a copy with this default
     */
    public SettingDefinition<T> withDefault(T value) {
        return new SettingDefinition<>(id, type, value, scope, displayName, description, category);
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
