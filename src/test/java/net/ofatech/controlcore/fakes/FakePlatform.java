package net.ofatech.controlcore.fakes;

import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.config.SettingStore;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;
import net.ofatech.controlcore.domain.ports.platform.Platform;
import net.ofatech.controlcore.domain.ports.platform.PortBinder;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;

/** In-memory host used instead of Hytale in tests. */
public final class FakePlatform implements Platform {

    public final RecordingLog log = new RecordingLog();
    public final FakeClock clock = new FakeClock();
    public final FakeSettings settings = new FakeSettings();
    public final FakePlayers players = new FakePlayers();
    public final net.ofatech.controlcore.infrastructure.i18n.BundledTranslations translations =
            new net.ofatech.controlcore.infrastructure.i18n.BundledTranslations();
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
    /** Permission nodes players do not have; everything else is granted. */
    public final java.util.Set<String> deniedNodes = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        binder.bind(net.ofatech.controlcore.domain.ports.system.Translations.class, translations);
        binder.bind(Messenger.class, messenger);
        binder.bind(Permissions.class, (player, node) -> !deniedNodes.contains(node));
        binder.bind(PlayerData.class, playerData);
        binder.bind(SettingStore.class, settingStore);
        binder.bind(Scheduler.class, scheduler);
        binder.bind(CommandRegistrar.class, commands);
        binder.bind(ModLookup.class, mods);
        binder.bind(ServerConfiguration.class, serverConfig);
        binder.bind(ControlPanelView.class, controlPanel);
        binder.bind(net.ofatech.controlcore.domain.ports.game.PlayerInput.class, input);
        binder.bind(net.ofatech.controlcore.domain.ports.game.CommandRunner.class, commandRunner);
        binder.bind(net.ofatech.controlcore.domain.ports.config.KeyBindStore.class, keyBinds);
        binder.bind(net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore.class, customKeyBinds);
        binder.bind(net.ofatech.controlcore.domain.ports.config.SettingPermissionStore.class, settingPermissions);
        binder.bind(InputProbe.class, new InputProbe() {
            @Override
            public boolean toggle(net.ofatech.controlcore.domain.model.PlayerId player) {
                return false;
            }

            @Override
            public net.ofatech.controlcore.domain.result.Result<Void> openKeyTest(
                    net.ofatech.controlcore.domain.model.PlayerId player, String target) {
                return net.ofatech.controlcore.domain.result.Result.success();
            }
        });
    }

    @Override
    public void connect(EventPublisher publisher) {
        connectedPublisher = publisher;
    }
}
