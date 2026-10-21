package net.ofatech.controlcore.domain.model.settings;

public enum SettingRights {
    NONE,
    VIEW,
    EDIT;

    public boolean canView() {
        return this != NONE;
    }

    public boolean canEdit() {
        return this == EDIT;
    }
}
