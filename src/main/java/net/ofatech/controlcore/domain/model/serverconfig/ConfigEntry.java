package net.ofatech.controlcore.domain.model.serverconfig;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;

/**
 * One key of the host server's own configuration file, as the control panel shows it.
 *
 * @param path key path in the file, for example {@code ConnectionTimeouts.PlayTimeout}
 * @param section heading the key is listed under, for example {@code Connection timeouts}
 * @param name display name, for example {@code Play timeout}
 * @param description what the key does; may be empty
 * @param kind how the value is written as text
 * @param options allowed values of a {@link ConfigValueKind#CHOICE}; empty otherwise
 * @param min smallest allowed value of an {@link ConfigValueKind#INTEGER} or {@link ConfigValueKind#SECONDS}
 * @param max largest allowed value of an {@link ConfigValueKind#INTEGER} or {@link ConfigValueKind#SECONDS}
 * @param value effective value as text; empty when unset
 * @param defaultValue the value of a fresh server, if it has one
 * @param access whether the key can be changed and when a change applies
 */
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
