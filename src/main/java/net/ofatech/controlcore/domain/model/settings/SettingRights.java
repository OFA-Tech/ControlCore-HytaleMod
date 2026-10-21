package net.ofatech.controlcore.domain.model.settings;

/** What one player may do with one setting. */
public enum SettingRights {
    /** The setting is not shown. */
    NONE,
    /** Shown read-only. */
    VIEW,
    /** Shown and changeable. */
    EDIT;

    public boolean canView() {
        return this != NONE;
    }

    public boolean canEdit() {
        return this == EDIT;
    }
}
