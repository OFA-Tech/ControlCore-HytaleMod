package net.ofatech.controlcore.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class FakeServerConfiguration implements ServerConfiguration {

    public final ServerSettings settings;
    public final List<ConfigEntry> entries = new CopyOnWriteArrayList<>();
    public final List<String> writes = new CopyOnWriteArrayList<>();
    public volatile boolean failWrites;

    public FakeServerConfiguration(ServerSettings settings) {
        this.settings = settings;
        entries.add(
                entry("Version", "General", "Version", ConfigValueKind.INTEGER, "4", null, SettingAccess.READ_ONLY));
        entries.add(entry(
                "ServerName",
                "General",
                "Server name",
                ConfigValueKind.TEXT,
                settings.serverName(),
                "Hytale Server",
                SettingAccess.EDITABLE));
        entries.add(entry(
                "MaxViewRadius",
                "General",
                "Max view radius",
                ConfigValueKind.INTEGER,
                Integer.toString(settings.maxViewRadius()),
                "32",
                SettingAccess.EDITABLE));
        entries.add(entry(
                "Password",
                "General",
                "Password",
                ConfigValueKind.SUMMARY,
                "None (public server)",
                null,
                SettingAccess.READ_ONLY));
        entries.add(entry(
                "Defaults.GameMode",
                "Defaults",
                "Game mode",
                ConfigValueKind.CHOICE,
                "Creative",
                "Adventure",
                SettingAccess.APPLIES_AFTER_RESTART));
        entries.add(entry(
                "ConnectionTimeouts.PlayTimeout",
                "Connection timeouts",
                "Play timeout (seconds)",
                ConfigValueKind.SECONDS,
                Long.toString(settings.playTimeout().toSeconds()),
                "60",
                SettingAccess.EDITABLE));
        entries.add(entry(
                "Backup.Enabled",
                "Backup",
                "Enabled",
                ConfigValueKind.BOOLEAN,
                "false",
                "false",
                SettingAccess.APPLIES_AFTER_RESTART));
        entries.add(entry(
                "Update.Patchline",
                "Update",
                "Patchline",
                ConfigValueKind.TEXT,
                "",
                null,
                SettingAccess.APPLIES_AFTER_RESTART));
        entries.add(entry(
                "BanStorage",
                "Advanced (read-only)",
                "Ban storage",
                ConfigValueKind.SUMMARY,
                "Disk (bans.json)",
                null,
                SettingAccess.READ_ONLY));
    }

    @Override
    public ServerSettings read() {
        return settings;
    }

    @Override
    public List<ConfigEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public Result<ConfigEntry> write(String path, String value) {
        for (int i = 0; i < entries.size(); i++) {
            ConfigEntry entry = entries.get(i);
            if (!entry.path().equals(path)) {
                continue;
            }
            if (entry.access() == SettingAccess.READ_ONLY) {
                return Result.failure(Error.forbidden("ServerConfig.ReadOnly", path + " cannot be changed here."));
            }
            if (failWrites) {
                return Result.failure(Error.validation("ServerConfig.InvalidValue", path + ": rejected"));
            }
            writes.add(path + "=" + value);
            ConfigEntry changed = new ConfigEntry(
                    entry.path(),
                    entry.section(),
                    entry.name(),
                    entry.description(),
                    entry.kind(),
                    entry.options(),
                    entry.min(),
                    entry.max(),
                    value,
                    entry.defaultValue(),
                    entry.access());
            entries.set(i, changed);
            return Result.success(changed);
        }
        return Result.failure(Error.notFound("ServerConfig.UnknownKey", "config.json has no key " + path + "."));
    }

    public Optional<ConfigEntry> entry(String path) {
        return entries.stream().filter(entry -> entry.path().equals(path)).findFirst();
    }

    private static ConfigEntry entry(
            String path,
            String section,
            String name,
            ConfigValueKind kind,
            String value,
            String defaultValue,
            SettingAccess access) {
        List<String> options = kind == ConfigValueKind.CHOICE ? List.of("Adventure", "Creative") : new ArrayList<>();
        int min = kind == ConfigValueKind.SECONDS ? 1 : kind == ConfigValueKind.INTEGER ? 1 : 0;
        int max = kind == ConfigValueKind.SECONDS ? 86_400 : kind == ConfigValueKind.INTEGER ? 32 : 0;
        return new ConfigEntry(
                path, section, name, "", kind, options, min, max, value, Optional.ofNullable(defaultValue), access);
    }
}
