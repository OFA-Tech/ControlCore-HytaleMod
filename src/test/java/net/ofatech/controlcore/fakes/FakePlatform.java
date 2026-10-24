package net.ofatech.controlcore.fakes;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.config.ChangeLogStore;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;
import net.ofatech.controlcore.domain.ports.config.SettingStore;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.CommandRunner;
import net.ofatech.controlcore.domain.ports.game.EmptyHandInput;
import net.ofatech.controlcore.domain.ports.game.HudDisplay;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;
import net.ofatech.controlcore.domain.ports.platform.Platform;
import net.ofatech.controlcore.domain.ports.platform.PortBinder;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
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
    public final RecordingCommandRunner commandRunner = new RecordingCommandRunner();
    public final FakeKeyBindStore keyBinds = new FakeKeyBindStore();
    public final FakeCustomKeyBindStore customKeyBinds = new FakeCustomKeyBindStore();
    public final FakeSettingPermissionStore settingPermissions = new FakeSettingPermissionStore();
    public final FakeChangeLogStore changeLog = new FakeChangeLogStore();
    public volatile Boolean emptyHandKeys;

    public final FakeModCatalog modCatalog = new FakeModCatalog();
    public final Map<PlayerId, Set<HudPart>> hiddenHud = new ConcurrentHashMap<>();
    public final Set<String> deniedNodes = ConcurrentHashMap.newKeySet();
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
        binder.bind(Clock.class, clock);
        binder.bind(SettingsProvider.class, settings);
        binder.bind(Players.class, players);
        binder.bind(Translations.class, translations);
        binder.bind(Messenger.class, messenger);
        binder.bind(Permissions.class, (player, node) -> !deniedNodes.contains(node));
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
        binder.bind(SettingPermissionStore.class, settingPermissions);
        binder.bind(ChangeLogStore.class, changeLog);
        binder.bind(EmptyHandInput.class, on -> emptyHandKeys = on);
        binder.bind(HudDisplay.class, (player, hide, show) -> {
            var hidden = hiddenHud.computeIfAbsent(player, key -> EnumSet.noneOf(HudPart.class));
            hidden.removeAll(show);
            hidden.addAll(hide);
        });
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
