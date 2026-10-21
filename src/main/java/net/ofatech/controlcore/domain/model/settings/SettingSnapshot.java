package net.ofatech.controlcore.domain.model.settings;

import net.ofatech.controlcore.domain.guard.Guard;

/**
 * The effective value of a setting at one moment, for lists and UIs. Returned by {@link SettingsEditor}.
 *
 * @param definition the setting
 * @param value effective value as text, in the form {@link SettingType#format(Object)} writes
 * @param overridden whether the value was set explicitly ({@code false} means it is the default)
 * @param access whether the setting can be changed and when a change applies (since API 1.2.0)
 */
public record SettingSnapshot(SettingDefinition<?> definition, String value, boolean overridden, SettingAccess access) {

    /**
     * Creates a snapshot.
     *
     * @param definition the setting
     * @param value effective value as text
     * @param overridden whether the value was set explicitly
     * @param access whether the setting can be changed and when a change applies
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code definition}, {@code value} or
     *     {@code access} is {@code null}
     */
    public SettingSnapshot {
        Guard.notNull(definition, "definition");
        Guard.notNull(value, "value");
        Guard.notNull(access, "access");
    }

    /**
     * Creates a snapshot of an editable setting whose changes apply straight away ({@link SettingAccess#EDITABLE}),
     * as for every registered setting.
     *
     * @param definition the setting
     * @param value effective value as text
     * @param overridden whether the value was set explicitly
     * @throws net.ofatech.controlcore.domain.guard.DomainException if {@code definition} or {@code value} is
     *     {@code null}
     */
    public SettingSnapshot(SettingDefinition<?> definition, String value, boolean overridden) {
        this(definition, value, overridden, SettingAccess.EDITABLE);
    }

    /**
     * Returns the setting's id, a shortcut for {@code definition().id()}.
     *
     * @return the id, for example {@code homes:max-homes}
     */
    public SettingId id() {
        return definition.id();
    }
}
