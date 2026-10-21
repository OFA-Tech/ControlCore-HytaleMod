package net.ofatech.controlcore.domain.ports.config;

import java.util.Map;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;

public interface SettingPermissionStore {

    Map<String, SettingPermission> all();

    void put(String target, SettingPermission rule);
}
