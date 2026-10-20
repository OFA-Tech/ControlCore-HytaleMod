package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;

public final class FakeSettingPermissionStore implements SettingPermissionStore {

    public final Map<String, SettingPermission> rules = new TreeMap<>();

    @Override
    public synchronized Map<String, SettingPermission> all() {
        return Map.copyOf(rules);
    }

    @Override
    public synchronized void put(String target, SettingPermission rule) {
        if (rule.isInherit()) {
            rules.remove(target);
        } else {
            rules.put(target, rule);
        }
    }
}
