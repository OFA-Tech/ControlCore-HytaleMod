package net.ofatech.controlcore.fakes;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.config.ActiveProfileStore;
import net.ofatech.controlcore.domain.ports.config.AutomationRuleStore;
import net.ofatech.controlcore.domain.ports.config.ChangeLogStore;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;
import net.ofatech.controlcore.domain.ports.config.KeyGameActionStore;
import net.ofatech.controlcore.domain.ports.config.ReportWriter;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;
import net.ofatech.controlcore.domain.ports.config.SettingStore;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.config.SnapshotStore;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.Bans;
import net.ofatech.controlcore.domain.ports.game.CommandRunner;
import net.ofatech.controlcore.domain.ports.game.EmptyHandInput;
import net.ofatech.controlcore.domain.ports.game.HudDisplay;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.KeyCapture;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;
import net.ofatech.controlcore.domain.ports.game.PlayerTuning;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.game.Worlds;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;
import net.ofatech.controlcore.domain.ports.platform.Platform;
import net.ofatech.controlcore.domain.ports.platform.PortBinder;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.ports.system.RecentProblems;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;
import net.ofatech.controlcore.domain.ports.system.Translations;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.infrastructure.i18n.BundledTranslations;

public final class FakePlatform implements Platform {

    public final RecordingLog log = new RecordingLog();
    public final FakeClock clock = new FakeClock();
    public final FakeSettings settings = new FakeSettings();
    public final FakePlayers players = new FakePlayers();
    public final BundledTranslations translations = new BundledTranslations();
    public final FakeMessenger messenger = new FakeMessenger();
    public final FakePlayerData playerData = new FakePlayerData();
    public final FakeSettingStore settingStore = new FakeSettingStore();
    public final FakeScheduler scheduler = new FakeScheduler();
    public final FakeCommandRegistrar commands = new FakeCommandRegistrar();
    public final FakeModLookup mods = new FakeModLookup();
    public final RecordingControlPanelView controlPanel = new RecordingControlPanelView();
    public final FakePlayerInput input = new FakePlayerInput();
    public final FakeKeyCapture keyCapture = new FakeKeyCapture();
    public final RecordingCommandRunner commandRunner = new RecordingCommandRunner();
    public final FakeKeyBindStore keyBinds = new FakeKeyBindStore();
    public final FakeCustomKeyBindStore customKeyBinds = new FakeCustomKeyBindStore();
    public final FakeKeyGameActionStore keyGameActions = new FakeKeyGameActionStore();
    public final FakeActiveProfileStore activeProfile = new FakeActiveProfileStore();
    public final FakeAutomationRuleStore automationRules = new FakeAutomationRuleStore();
    public final FakeServerHealth health = new FakeServerHealth();
    public final FakeOverlayDisplay overlay = new FakeOverlayDisplay();
    public final FakePlayerAdmin admin = new FakePlayerAdmin();
    public final FakeAccessLists access = new FakeAccessLists();
    public final FakeChatGate chat = new FakeChatGate();
    public final java.util.Map<PlayerId, net.ofatech.controlcore.domain.ports.game.PlayerContext.Context> context =
            new java.util.concurrent.ConcurrentHashMap<>();
    public final FakeReportWriter reports = new FakeReportWriter();
    public final List<RecentProblems.Problem> problems = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final FakeSettingPermissionStore settingPermissions = new FakeSettingPermissionStore();
    public final FakeChangeLogStore changeLog = new FakeChangeLogStore();
    public final FakeSnapshotStore snapshots = new FakeSnapshotStore();
    public final FakeWorlds worlds = new FakeWorlds().add("default");
    public final FakePlayerTuning tuning = new FakePlayerTuning();
    public final FakeBans bans = new FakeBans();
    public volatile Boolean emptyHandKeys;

    public final FakeModCatalog modCatalog = new FakeModCatalog();
    public final Map<PlayerId, Set<HudPart>> hiddenHud = new ConcurrentHashMap<>();
    public final Set<String> deniedNodes = ConcurrentHashMap.newKeySet();
    public final java.util.Map<PlayerId, java.util.List<String>> deniedNodesFor = new ConcurrentHashMap<>();
    public final Set<PlayerId> probing = ConcurrentHashMap.newKeySet();

    public final FakeServerConfiguration serverConfig = new FakeServerConfiguration(
            new ServerSettings("Test Server", "Welcome", false, 10, 5, java.time.Duration.ofMinutes(10)));
    public EventPublisher connectedPublisher;

    @Override
    public String name() {
        return "Fake";
    }

    @Override
    public void bind(PortBinder binder) {
        binder.bind(Log.class, log);
        binder.bind(RecentProblems.class, () -> problems);
        binder.bind(ServerHealth.class, health);
        binder.bind(ReportWriter.class, reports);
        binder.bind(Clock.class, clock);
        binder.bind(SettingsProvider.class, settings);
        binder.bind(Players.class, players);
        binder.bind(Translations.class, translations);
        binder.bind(Messenger.class, messenger);
        binder.bind(
                Permissions.class,
                (player, node) -> !deniedNodes.contains(node)
                        && !deniedNodesFor
                                .getOrDefault(player, java.util.List.of())
                                .contains(node));
        binder.bind(PlayerData.class, playerData);
        binder.bind(SettingStore.class, settingStore);
        binder.bind(Scheduler.class, scheduler);
        binder.bind(CommandRegistrar.class, commands);
        binder.bind(ModLookup.class, mods);
        binder.bind(ModCatalog.class, modCatalog);
        binder.bind(ServerConfiguration.class, serverConfig);
        binder.bind(ControlPanelView.class, controlPanel);
        binder.bind(PlayerInput.class, input);
        binder.bind(CommandRunner.class, commandRunner);
        binder.bind(KeyBindStore.class, keyBinds);
        binder.bind(CustomKeyBindStore.class, customKeyBinds);
        binder.bind(KeyGameActionStore.class, keyGameActions);
        binder.bind(SettingPermissionStore.class, settingPermissions);
        binder.bind(ChangeLogStore.class, changeLog);
        binder.bind(SnapshotStore.class, snapshots);
        binder.bind(ActiveProfileStore.class, activeProfile);
        binder.bind(AutomationRuleStore.class, automationRules);
        binder.bind(Worlds.class, worlds);
        binder.bind(PlayerTuning.class, tuning);
        binder.bind(Bans.class, bans);
        binder.bind(EmptyHandInput.class, on -> emptyHandKeys = on);
        binder.bind(HudDisplay.class, (player, hide, show) -> {
            var hidden = hiddenHud.computeIfAbsent(player, key -> EnumSet.noneOf(HudPart.class));
            hidden.removeAll(show);
            hidden.addAll(hide);
        });
        binder.bind(net.ofatech.controlcore.domain.ports.game.OverlayDisplay.class, overlay);
        binder.bind(net.ofatech.controlcore.domain.ports.game.PlayerAdmin.class, admin);
        binder.bind(net.ofatech.controlcore.domain.ports.game.AccessLists.class, access);
        binder.bind(net.ofatech.controlcore.domain.ports.game.ChatGate.class, chat);
        binder.bind(
                net.ofatech.controlcore.domain.ports.game.PlayerContext.class,
                player -> context.getOrDefault(
                        player, net.ofatech.controlcore.domain.ports.game.PlayerContext.Context.UNKNOWN));
        binder.bind(KeyCapture.class, keyCapture);
        binder.bind(InputProbe.class, new InputProbe() {
            @Override
            public boolean toggle(PlayerId player) {
                return probing.add(player) || !probing.remove(player);
            }

            @Override
            public boolean isOn(PlayerId player) {
                return probing.contains(player);
            }

            @Override
            public Result<Void> openKeyTest(PlayerId player, String target) {
                return Result.success();
            }
        });
    }

    @Override
    public void connect(EventPublisher publisher) {
        connectedPublisher = publisher;
    }
}
