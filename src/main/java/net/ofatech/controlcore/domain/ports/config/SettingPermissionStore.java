package net.ofatech.controlcore.domain.ports.config;

import java.util.Map;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;

/**
 * Admins' rules for who may see and change settings, by target: {@code setting:<id>}, {@code category:<name>}
 * or {@code mod:<mod id>}.
 */
public interface SettingPermissionStore {

    /** @return every rule, sorted by target */
    Map<String, SettingPermission> all();

    /** Adds or replaces the rule of {@code target} and saves at once; an inherit rule removes it. */
    void put(String target, SettingPermission rule);
}
