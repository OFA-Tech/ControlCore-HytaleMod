package net.ofatech.controlcore.application.worlds;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.game.PlayerTuning;
import net.ofatech.controlcore.domain.ports.game.PlayerTuning.Tuning;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.game.Worlds;

public final class WorldsService {

    static final Duration SETTLE = Duration.ofMillis(750);
    private static final Text PERFORMANCE = Text.key("controlcore.settings.category.performance", "Performance");
    private static final Text MOVEMENT = Text.key("controlcore.settings.category.movement", "Movement");

    private final ServerConfigEditor serverConfig;
    private final Worlds worlds;
    private final PlayerTuning tuning;
    private final Players players;
    private final Scheduler scheduler;
    private final Setting<Boolean> pauseEmpty;
    private final Setting<Integer> sectionsPerSecond;
    private final Setting<Integer> sectionsPerTick;
    private final Setting<Integer> speed;
    private final Setting<Integer> jump;
    private final Set<String> pausedByUs = ConcurrentHashMap.newKeySet();

    public WorldsService(
            SettingsApi settings,
            ServerConfigEditor serverConfig,
            Worlds worlds,
            PlayerTuning tuning,
            Players players,
            Scheduler scheduler) {
        this.serverConfig = serverConfig;
        this.worlds = worlds;
        this.tuning = tuning;
        this.players = players;
        this.scheduler = scheduler;
        this.pauseEmpty = settings.register(SettingDefinition.server(
                                "controlcore", "pause-empty-worlds", SettingType.bool(), false)
                        .named(Text.key("controlcore.settings.pause_empty_worlds.name", "Pause empty worlds"))
                        .describedAs(Text.key(
                                "controlcore.settings.pause_empty_worlds.desc",
                                "Worlds nobody is in stop updating (saves CPU) and resume when a player arrives."))
                        .inCategory(PERFORMANCE))
                .value();
        this.sectionsPerSecond = settings.register(SettingDefinition.server(
                                "controlcore", "chunk-sections-per-second", SettingType.integer(0, 2560), 0)
                        .named(Text.key(
                                "controlcore.settings.chunk_sections_per_second.name", "Chunk loading per second"))
                        .describedAs(Text.key(
                                "controlcore.settings.chunk_sections_per_second.desc",
                                "Most chunk sections sent to each player per second; 0 keeps Hytale's own limit."
                                        + " Lower smooths CPU and bandwidth when many players explore."))
                        .inCategory(PERFORMANCE))
                .value();
        this.sectionsPerTick = settings.register(SettingDefinition.server(
                                "controlcore", "chunk-sections-per-tick", SettingType.integer(0, 256), 0)
                        .named(Text.key("controlcore.settings.chunk_sections_per_tick.name", "Chunk loading per tick"))
                        .describedAs(Text.key(
                                "controlcore.settings.chunk_sections_per_tick.desc",
                                "Most chunk sections sent to each player per tick; 0 keeps Hytale's own limit."))
                        .inCategory(PERFORMANCE))
                .value();
        this.speed = settings.register(SettingDefinition.server(
                                "controlcore", "player-speed", SettingType.integer(10, 500), 100)
                        .named(Text.key("controlcore.settings.player_speed.name", "Player speed (%)"))
                        .describedAs(Text.key(
                                "controlcore.settings.player_speed.desc",
                                "Movement speed of every player; a world can override it in Server Configs → Worlds."))
                        .inCategory(MOVEMENT))
                .value();
        this.jump = settings.register(SettingDefinition.server(
                                "controlcore", "player-jump", SettingType.integer(10, 500), 100)
                        .named(Text.key("controlcore.settings.player_jump.name", "Jump strength (%)"))
                        .describedAs(Text.key(
                                "controlcore.settings.player_jump.desc",
                                "Jump strength of every player; a world can override it in Server Configs → Worlds."))
                        .inCategory(MOVEMENT))
                .value();
    }

    public void start() {
        worlds.applySaved();
        sectionsPerSecond.onChange(change -> tuneEveryone());
        sectionsPerTick.onChange(change -> tuneEveryone());
        speed.onChange(change -> tuneEveryone());
        jump.onChange(change -> tuneEveryone());
        pauseEmpty.onChange(change -> checkEmptyWorlds());
        serverConfig.onAnyChange(change -> {
            String key = change.id().key();
            if (ServerConfigEditor.isWorld(change.id())
                    && (key.endsWith("." + suffix(Worlds.SPEED)) || key.endsWith("." + suffix(Worlds.JUMP)))) {
                tuneEveryone();
            }
        });
        tuneEveryone();
    }

    public void stop() {
        pausedByUs.forEach(world -> worlds.setPaused(world, false));
        pausedByUs.clear();
    }

    public void tick() {
        worlds.applySaved();
        checkEmptyWorlds();
    }

    public void tuneLater(PlayerInfo player) {
        scheduler.runLater(SETTLE, () -> tune(player.id()));
    }

    public void entered(PlayerInfo player, String world) {
        if (pausedByUs.remove(world)) {
            worlds.setPaused(world, false);
        }
        tuneLater(player);
    }

    public Tuning tuningFor(PlayerId player) {
        Optional<String> world = tuning.worldOf(player);
        int speedPercent = world.flatMap(name -> override(name, Worlds.SPEED)).orElse(speed.get());
        int jumpPercent = world.flatMap(name -> override(name, Worlds.JUMP)).orElse(jump.get());
        return new Tuning(speedPercent, jumpPercent, sectionsPerSecond.get(), sectionsPerTick.get());
    }

    private void tune(PlayerId player) {
        tuning.apply(player, tuningFor(player));
    }

    private void tuneEveryone() {
        players.online().forEach(player -> tune(player.id()));
    }

    private void checkEmptyWorlds() {
        boolean enabled = pauseEmpty.get();
        for (String world : worlds.names()) {
            boolean empty = worlds.players(world) == 0;
            if (enabled && empty && !worlds.isPaused(world)) {
                worlds.setPaused(world, true);
                pausedByUs.add(world);
            } else if ((!enabled || !empty) && pausedByUs.remove(world)) {
                worlds.setPaused(world, false);
            }
        }
    }

    private Optional<Integer> override(String world, String path) {
        var read = serverConfig.read(ServerConfigEditor.worldIdOf(world, path), Optional.empty());
        return (read.isSuccess() ? Optional.of(read.value()) : Optional.<SettingSnapshot>empty())
                .map(SettingSnapshot::value)
                .flatMap(WorldsService::number)
                .filter(percent -> percent > 0);
    }

    private static Optional<Integer> number(String text) {
        try {
            return Optional.of(Integer.parseInt(text.strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String suffix(String path) {
        return ServerConfigEditor.idOf(path).key();
    }
}
