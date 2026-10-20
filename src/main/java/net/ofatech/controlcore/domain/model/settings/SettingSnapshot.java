package net.ofatech.controlcore.domain.model.settings;

import net.ofatech.controlcore.domain.guard.Guard;

/**
 * The effective value of a setting at one moment, for lists and UIs.
 *
 * @param definition the setting
 * @param value effective value as text
 * @param overridden whether the value was set explicitly ({@code false} means it is the default)
 * @param access whether the setting can be changed and when a change applies (since API 1.2.0)
 */
public record SettingSnapshot(SettingDefinition<?> definition, String value, boolean overridden, SettingAccess access) {

    public SettingSnapshot {
        Guard.notNull(definition, "definition");
        Guard.notNull(value, "value");
        Guard.notNull(access, "access");
    }

    /** An editable setting whose changes apply straight away (every registered setting). */
    public SettingSnapshot(SettingDefinition<?> definition, String value, boolean overridden) {
        this(definition, value, overridden, SettingAccess.EDITABLE);
    }

    /** @return the setting's id */
    public SettingId id() {
        return definition.id();
    }
}
