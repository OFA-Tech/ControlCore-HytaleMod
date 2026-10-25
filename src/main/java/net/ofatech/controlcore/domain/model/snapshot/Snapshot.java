package net.ofatech.controlcore.domain.model.snapshot;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public record Snapshot(
        String name,
        Instant createdAt,
        String createdBy,
        Set<Part> parts,
        Set<String> mods,
        Map<String, String> config,
        Map<String, String> settings,
        Map<String, String> serverKeys,
        List<CustomKeyBind> customBinds,
        Map<String, SettingPermission> permissions,
        Map<String, Boolean> modsEnabled,
        Map<String, Map<String, String>> players) {

    public enum Part {
        CONFIG,
        WORLDS,
        SETTINGS,
        KEYS,
        PERMISSIONS,
        MODS,
        PLAYERS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Part> of(String text) {
            return Arrays.stream(values())
                    .filter(part -> part.id().equalsIgnoreCase(text == null ? "" : text.strip()))
                    .findFirst();
        }
    }

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}");

    public Snapshot {
        Guard.notNull(name, "name");
        Guard.notNull(createdAt, "createdAt");
        createdBy = createdBy == null ? "" : createdBy;
        parts = parts == null || parts.isEmpty() ? EnumSet.noneOf(Part.class) : EnumSet.copyOf(parts);
        mods = mods == null ? Set.of() : Set.copyOf(mods);
        config = config == null ? Map.of() : Map.copyOf(config);
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        serverKeys = serverKeys == null ? Map.of() : Map.copyOf(serverKeys);
        customBinds = customBinds == null ? List.of() : List.copyOf(customBinds);
        permissions = permissions == null ? Map.of() : Map.copyOf(permissions);
        modsEnabled = modsEnabled == null ? Map.of() : Map.copyOf(modsEnabled);
        players = players == null ? Map.of() : Map.copyOf(players);
    }

    public boolean has(Part part) {
        return parts.contains(part);
    }

    public static Result<String> validName(String text) {
        String name = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (NAME.matcher(name).matches()) {
            return Result.success(name);
        }
        return Result.failure(Error.validation(
                "Snapshots.InvalidName",
                Text.key(
                                "controlcore.errors.snapshots.invalid_name",
                                "'{name}' can't be a snapshot name: use 1 to 40 letters, digits, - or _.")
                        .with("name", text == null ? "" : text)));
    }
}
