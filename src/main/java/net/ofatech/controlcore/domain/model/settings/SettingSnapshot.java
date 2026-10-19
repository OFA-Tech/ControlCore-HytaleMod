package net.ofatech.controlcore.domain.model.settings;

/**
 * The effective value of a setting at one moment, for lists and UIs.
 *
 * @param definition the setting
 * @param value effective value as text
 * @param overridden whether the value was set explicitly ({@code false} means it is the default)
 */
public record SettingSnapshot(SettingDefinition<?> definition, String value, boolean overridden) {

    /** @return the setting's id */
    public SettingId id() {
        return definition.id();
    }
}
