package net.ofatech.controlcore.domain.model.settings;

/** Whether and when a change to a setting takes effect. */
public enum SettingAccess {
    /** Editable; the change applies straight away. */
    EDITABLE,
    /** Editable; the change is saved but only applies after a server restart. */
    APPLIES_AFTER_RESTART,
    /** Shown but not editable. */
    READ_ONLY
}
