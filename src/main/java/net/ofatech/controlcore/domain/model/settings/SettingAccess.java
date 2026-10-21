package net.ofatech.controlcore.domain.model.settings;

/** Whether and when a change to a setting takes effect, as reported in a {@link SettingSnapshot}. */
public enum SettingAccess {
    /** Editable; the change applies straight away; mod settings have this unless the viewer may only see them. */
    EDITABLE,
    /** Editable; the change is saved but only applies after a server restart. */
    APPLIES_AFTER_RESTART,
    /** Shown but not editable, for example to a player who may see the setting but not change it. */
    READ_ONLY
}
