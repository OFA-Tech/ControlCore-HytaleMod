package net.ofatech.controlcore.domain.model.serverconfig;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;

public record ConfigEntry(
        String path,
        String section,
        String name,
        String description,
        ConfigValueKind kind,
        List<String> options,
        int min,
        int max,
        String value,
        Optional<String> defaultValue,
        SettingAccess access) {

    public ConfigEntry {
        Guard.notBlank(path, "path");
        Guard.notBlank(section, "section");
        Guard.notBlank(name, "name");
        Guard.notNull(description, "description");
        Guard.notNull(kind, "kind");
        Guard.notNull(value, "value");
        Guard.notNull(defaultValue, "defaultValue");
        Guard.notNull(access, "access");
        options = List.copyOf(options);
        if (min > max) {
            throw new DomainException("min " + min + " is greater than max " + max + " for " + path);
        }
        if (kind == ConfigValueKind.CHOICE && options.isEmpty()) {
            throw new DomainException("A choice needs options: " + path);
        }
        if (kind == ConfigValueKind.SUMMARY && access != SettingAccess.READ_ONLY) {
            throw new DomainException("A summary is always read-only: " + path);
        }
    }
}
