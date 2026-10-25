package net.ofatech.controlcore.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.ports.game.Worlds;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class FakeWorlds implements Worlds {

    public final Map<String, Map<String, String>> values = new ConcurrentHashMap<>();
    public final Map<String, Integer> players = new ConcurrentHashMap<>();
    public final Map<String, Boolean> paused = new ConcurrentHashMap<>();
    public int applied;

    public FakeWorlds add(String world) {
        Map<String, String> config = new ConcurrentHashMap<>();
        config.put("IsPvpEnabled", "false");
        config.put("Tps", "30");
        config.put(SPEED, "0");
        config.put(JUMP, "0");
        values.put(world, config);
        return this;
    }

    @Override
    public List<String> names() {
        return values.keySet().stream().sorted().toList();
    }

    @Override
    public List<ConfigEntry> entries(String world) {
        Map<String, String> config = values.get(world);
        if (config == null) {
            return List.of();
        }
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(
                "Status",
                "Performance",
                "Status",
                "",
                ConfigValueKind.SUMMARY,
                List.of(),
                0,
                0,
                players(world) + " players",
                Optional.empty(),
                SettingAccess.READ_ONLY));
        entries.add(new ConfigEntry(
                "IsPvpEnabled",
                "Gameplay",
                "PvP",
                "",
                ConfigValueKind.BOOLEAN,
                List.of(),
                0,
                0,
                config.get("IsPvpEnabled"),
                Optional.of("false"),
                SettingAccess.EDITABLE));
        entries.add(new ConfigEntry(
                "Tps",
                "Performance",
                "Ticks per second",
                "",
                ConfigValueKind.INTEGER,
                List.of(),
                1,
                120,
                config.get("Tps"),
                Optional.of("30"),
                SettingAccess.EDITABLE));
        entries.add(new ConfigEntry(
                SPEED,
                "Movement",
                "Player speed (%)",
                "",
                ConfigValueKind.INTEGER,
                List.of(),
                0,
                500,
                config.get(SPEED),
                Optional.of("0"),
                SettingAccess.EDITABLE));
        entries.add(new ConfigEntry(
                JUMP,
                "Movement",
                "Jump strength (%)",
                "",
                ConfigValueKind.INTEGER,
                List.of(),
                0,
                500,
                config.get(JUMP),
                Optional.of("0"),
                SettingAccess.EDITABLE));
        return entries;
    }

    @Override
    public Result<ConfigEntry> write(String world, String path, String value) {
        Map<String, String> config = values.get(world);
        if (config == null || !config.containsKey(path)) {
            return Result.failure(Error.notFound("Worlds.Unknown", world + " " + path));
        }
        config.put(path, value);
        return Result.success(entries(world).stream()
                .filter(entry -> entry.path().equals(path))
                .findFirst()
                .get());
    }

    @Override
    public int players(String world) {
        return players.getOrDefault(world, 0);
    }

    @Override
    public boolean isPaused(String world) {
        return paused.getOrDefault(world, false);
    }

    @Override
    public void setPaused(String world, boolean value) {
        paused.put(world, value);
    }

    @Override
    public void applySaved() {
        applied++;
    }
}
