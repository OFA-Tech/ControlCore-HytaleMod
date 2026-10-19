package net.ofatech.controlcore.domain.model.settings;

/** Who a setting's value belongs to. */
public enum SettingScope {
    /** One value for the whole server, changed by admins. */
    SERVER,
    /** One value per player (a preference); players without their own value get the default. */
    PLAYER
}
