package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.config.SettingStore;
import net.ofatech.controlcore.domain.ports.game.Worlds;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class HytaleWorlds implements Worlds {

    static final String GAMEPLAY = "Gameplay";
    static final String NPCS = "NPCs";
    static final String PERFORMANCE = "Performance";
    static final String SAVING = "Saving";
    static final String MOVEMENT = "Movement";
    static final String ADVANCED = "Advanced (read-only)";
    static final String STATUS = "Status";
    static final String TPS = "Tps";
    static final int MAX_TPS = 120;
    static final int MAX_PERCENT = 500;

    private record Toggle(
            String path,
            String section,
            String name,
            String description,
            Function<WorldConfig, Boolean> get,
            BiConsumer<World, Boolean> set) {}

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle(
                    "IsPvpEnabled",
                    GAMEPLAY,
                    "PvP",
                    "Players can hurt each other.",
                    WorldConfig::isPvpEnabled,
                    (world, on) -> world.getWorldConfig().setPvpEnabled(on)),
            new Toggle(
                    "IsFallDamageEnabled",
                    GAMEPLAY,
                    "Fall damage",
                    "Falling hurts.",
                    WorldConfig::isFallDamageEnabled,
                    (world, on) -> world.getWorldConfig().setFallDamageEnabled(on)),
            new Toggle(
                    "IsGameTimePaused",
                    GAMEPLAY,
                    "Time paused",
                    "The day-night cycle stops.",
                    WorldConfig::isGameTimePaused,
                    (world, on) -> world.getWorldConfig().setGameTimePaused(on)),
            new Toggle(
                    "IsCompassUpdating",
                    GAMEPLAY,
                    "Compass updates",
                    "Players' compasses update.",
                    WorldConfig::isCompassUpdating,
                    (world, on) -> world.getWorldConfig().setCompassUpdating(on)),
            new Toggle(
                    "IsObjectiveMarkersEnabled",
                    GAMEPLAY,
                    "Objective markers",
                    "Objective markers are shown.",
                    WorldConfig::isObjectiveMarkersEnabled,
                    (world, on) -> world.getWorldConfig().setObjectiveMarkersEnabled(on)),
            new Toggle(
                    "IsSpawningNPC",
                    NPCS,
                    "NPC spawning",
                    "Creatures and NPCs spawn naturally.",
                    WorldConfig::isSpawningNPC,
                    (world, on) -> world.getWorldConfig().setSpawningNPC(on)),
            new Toggle(
                    "IsSpawnMarkersEnabled",
                    NPCS,
                    "Spawn markers",
                    "Spawn markers place their NPCs.",
                    WorldConfig::isSpawnMarkersEnabled,
                    (world, on) -> world.getWorldConfig().setIsSpawnMarkersEnabled(on)),
            new Toggle(
                    "IsAllNPCFrozen",
                    NPCS,
                    "Freeze all NPCs",
                    "NPCs stop moving and acting.",
                    WorldConfig::isAllNPCFrozen,
                    (world, on) -> world.getWorldConfig().setIsAllNPCFrozen(on)),
            new Toggle(
                    "IsTicking",
                    PERFORMANCE,
                    "Ticking",
                    "The world runs. Off freezes everything in it.",
                    WorldConfig::isTicking,
                    (world, on) -> {
                        world.getWorldConfig().setTicking(on);
                        world.setTicking(on);
                    }),
            new Toggle(
                    "IsBlockTicking",
                    PERFORMANCE,
                    "Block ticking",
                    "Crops grow, fire spreads and other blocks update.",
                    WorldConfig::isBlockTicking,
                    (world, on) -> world.getWorldConfig().setBlockTicking(on)),
            new Toggle(
                    "IsUnloadingChunks",
                    PERFORMANCE,
                    "Unload unused chunks",
                    "Chunks nobody is near are unloaded to save memory.",
                    WorldConfig::canUnloadChunks,
                    (world, on) -> world.getWorldConfig().setCanUnloadChunks(on)),
            new Toggle(
                    "IsSavingPlayers",
                    SAVING,
                    "Save players",
                    "Players' progress in this world is saved.",
                    WorldConfig::isSavingPlayers,
                    (world, on) -> world.getWorldConfig().setSavingPlayers(on)),
            new Toggle(
                    "IsSavingChunks",
                    SAVING,
                    "Save chunks",
                    "Changes to the world are saved.",
                    WorldConfig::canSaveChunks,
                    (world, on) -> world.getWorldConfig().setCanSaveChunks(on)),
            new Toggle(
                    "SaveNewChunks",
                    SAVING,
                    "Save new chunks",
                    "Newly generated chunks are saved.",
                    WorldConfig::shouldSaveNewChunks,
                    (world, on) -> world.getWorldConfig().setSaveNewChunks(on)));

    private final SettingStore extras;
    private final Log log;
    private final Set<String> tpsApplied = ConcurrentHashMap.newKeySet();

    public HytaleWorlds(SettingStore extras, Log log) {
        this.extras = extras;
        this.log = log;
    }

    @Override
    public List<String> names() {
        return Universe.get().getWorlds().keySet().stream().sorted().toList();
    }

    @Override
    public List<ConfigEntry> entries(String name) {
        World world = Universe.get().getWorld(name);
        if (world == null) {
            return List.of();
        }
        WorldConfig config = world.getWorldConfig();
        WorldConfig fresh = new WorldConfig();
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(info(STATUS, PERFORMANCE, "Status", "Right now, in this world.", status(world)));
        for (Toggle toggle : TOGGLES) {
            entries.add(new ConfigEntry(
                    toggle.path(),
                    toggle.section(),
                    toggle.name(),
                    toggle.description(),
                    ConfigValueKind.BOOLEAN,
                    List.of(),
                    0,
                    0,
                    Boolean.toString(toggle.get().apply(config)),
                    Optional.of(Boolean.toString(toggle.get().apply(fresh))),
                    SettingAccess.EDITABLE));
        }
        GameMode mode = config.getGameMode();
        entries.add(new ConfigEntry(
                "GameMode",
                GAMEPLAY,
                "Game mode",
                "Game mode of players who join this world.",
                ConfigValueKind.CHOICE,
                Arrays.stream(GameMode.VALUES).map(Enum::name).toList(),
                0,
                0,
                mode == null ? GameMode.Adventure.name() : mode.name(),
                Optional.of(
                        fresh.getGameMode() == null
                                ? GameMode.Adventure.name()
                                : fresh.getGameMode().name()),
                SettingAccess.EDITABLE));
        entries.add(new ConfigEntry(
                TPS,
                PERFORMANCE,
                "Ticks per second",
                "How often the world updates. Lower saves CPU but makes it run slower.",
                ConfigValueKind.INTEGER,
                List.of(),
                1,
                MAX_TPS,
                Integer.toString(world.getTps()),
                Optional.of(Integer.toString(TickingThread.TPS)),
                SettingAccess.EDITABLE));
        entries.add(percent(name, SPEED, "Player speed (%)", "Movement speed here; 0 uses the server-wide value."));
        entries.add(percent(name, JUMP, "Jump strength (%)", "Jump height here; 0 uses the server-wide value."));
        entries.add(info("Seed", ADVANCED, "Seed", "", Long.toString(config.getSeed())));
        entries.add(info(
                "GameplayConfig",
                ADVANCED,
                "Gameplay config",
                "",
                Optional.ofNullable(config.getGameplayConfig()).orElse("")));
        entries.add(info(
                "DeleteOnRemove", ADVANCED, "Delete when removed", "", Boolean.toString(config.isDeleteOnRemove())));
        return entries;
    }

    @Override
    public synchronized Result<ConfigEntry> write(String name, String path, String value) {
        World world = Universe.get().getWorld(name);
        if (world == null) {
            return Result.failure(Error.notFound(
                    "Worlds.Unknown",
                    Text.key("controlcore.errors.worlds.unknown", "There is no world {world}.")
                            .with("world", name)));
        }
        String text = value == null ? "" : value.strip();
        Optional<Toggle> toggle = TOGGLES.stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst();
        try {
            if (toggle.isPresent()) {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    return invalid(path, value);
                }
                toggle.get().set().accept(world, Boolean.parseBoolean(text));
                world.getWorldConfig().markChanged();
            } else if (path.equals("GameMode")) {
                GameMode mode = Arrays.stream(GameMode.VALUES)
                        .filter(candidate -> candidate.name().equalsIgnoreCase(text))
                        .findFirst()
                        .orElse(null);
                if (mode == null) {
                    return invalid(path, value);
                }
                world.getWorldConfig().setGameMode(mode);
                world.getWorldConfig().markChanged();
            } else if (path.equals(TPS)) {
                int tps = Integer.parseInt(text);
                if (tps < 1 || tps > MAX_TPS) {
                    return invalid(path, value);
                }
                world.execute(() -> world.setTps(tps));
                tpsApplied.add(name);
                if (tps == TickingThread.TPS) {
                    extras.remove(extraId(name, TPS));
                } else {
                    extras.put(extraId(name, TPS), Integer.toString(tps));
                }
                extras.flush();
            } else if (path.equals(SPEED) || path.equals(JUMP)) {
                int percent = Integer.parseInt(text);
                if (percent < 0 || percent > MAX_PERCENT || (percent > 0 && percent < 10)) {
                    return invalid(path, value);
                }
                if (percent == 0) {
                    extras.remove(extraId(name, path));
                } else {
                    extras.put(extraId(name, path), Integer.toString(percent));
                }
                extras.flush();
            } else {
                return Result.failure(Error.forbidden(
                        "ServerConfig.ReadOnly",
                        Text.key("controlcore.errors.serverconfig.read_only", "{name} cannot be changed here.")
                                .with("name", path)));
            }
        } catch (NumberFormatException e) {
            return invalid(path, value);
        } catch (RuntimeException e) {
            log.error("Could not change " + path + " of world " + name, e);
            return Result.failure(Error.failure("Worlds.WriteFailed", path + ": " + e.getMessage()));
        }
        return entries(name).stream()
                .filter(entry -> entry.path().equals(path))
                .map(entry -> path.equals(TPS) ? withValue(entry, text) : entry)
                .findFirst()
                .<Result<ConfigEntry>>map(Result::success)
                .orElseGet(() -> invalid(path, value));
    }

    @Override
    public int players(String name) {
        World world = Universe.get().getWorld(name);
        return world == null ? 0 : world.getPlayerCount();
    }

    @Override
    public boolean isPaused(String name) {
        World world = Universe.get().getWorld(name);
        return world != null && world.isPaused();
    }

    @Override
    public void setPaused(String name, boolean paused) {
        World world = Universe.get().getWorld(name);
        if (world != null && world.isPaused() != paused) {
            world.setPaused(paused);
            log.info((paused ? "Paused empty world " : "Resumed world ") + name);
        }
    }

    @Override
    public void applySaved() {
        for (World world : Universe.get().getWorlds().values()) {
            if (!tpsApplied.add(world.getName())) {
                continue;
            }
            extras.get(extraId(world.getName(), TPS)).ifPresent(text -> {
                try {
                    int tps = Integer.parseInt(text);
                    if (tps >= 1 && tps <= MAX_TPS && world.getTps() != tps) {
                        world.execute(() -> world.setTps(tps));
                    }
                } catch (NumberFormatException e) {
                    log.warn("Ignored the saved TPS \"" + text + "\" of world " + world.getName());
                }
            });
        }
    }

    private static ConfigEntry withValue(ConfigEntry entry, String value) {
        return new ConfigEntry(
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
    }

    private ConfigEntry percent(String world, String path, String name, String description) {
        return new ConfigEntry(
                path,
                MOVEMENT,
                name,
                description,
                ConfigValueKind.INTEGER,
                List.of(),
                0,
                MAX_PERCENT,
                extras.get(extraId(world, path)).orElse("0"),
                Optional.of("0"),
                SettingAccess.EDITABLE);
    }

    private static ConfigEntry info(String path, String section, String name, String description, String value) {
        return new ConfigEntry(
                path,
                section,
                name,
                description,
                ConfigValueKind.SUMMARY,
                List.of(),
                0,
                0,
                value,
                Optional.empty(),
                SettingAccess.READ_ONLY);
    }

    private static String status(World world) {
        int players = world.getPlayerCount();
        int chunks = world.getChunkStore().getLoadedChunksCount();
        String state = !world.isTicking() ? ", not ticking" : world.isPaused() ? ", paused" : "";
        return players + (players == 1 ? " player, " : " players, ") + chunks + " chunks loaded, " + world.getTps()
                + " TPS" + state;
    }

    static SettingId extraId(String world, String path) {
        String slug =
                world.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+", "");
        if (slug.isEmpty()) {
            slug = "world";
        }
        return new SettingId("world", (slug + "." + path.toLowerCase(Locale.ROOT)));
    }

    private static <T> Result<T> invalid(String path, String text) {
        return Result.failure(Error.validation(
                "ServerConfig.InvalidValue",
                Text.key("controlcore.errors.serverconfig.invalid_value", "\"{text}\" is not a valid {path}.")
                        .with("text", text == null ? "" : text, "path", path)));
    }
}
