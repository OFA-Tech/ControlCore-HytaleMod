package net.ofatech.controlcore.fakes;

import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
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
    public final FakeMessenger messenger = new FakeMessenger();
    public final FakePlayerData playerData = new FakePlayerData();
    public final FakeScheduler scheduler = new FakeScheduler();
    public final FakeCommandRegistrar commands = new FakeCommandRegistrar();
    public final FakeModLookup mods = new FakeModLookup();
    public final RecordingControlPanelView controlPanel = new RecordingControlPanelView();
    public ServerSettings server =
            new ServerSettings("Test Server", "Welcome", false, 10, 5, java.time.Duration.ofMinutes(10));
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
        binder.bind(Messenger.class, messenger);
        binder.bind(Permissions.class, (player, node) -> true);
        binder.bind(PlayerData.class, playerData);
        binder.bind(Scheduler.class, scheduler);
        binder.bind(CommandRegistrar.class, commands);
        binder.bind(ModLookup.class, mods);
        binder.bind(ServerConfiguration.class, () -> server);
        binder.bind(ControlPanelView.class, controlPanel);
    }

    @Override
    public void connect(EventPublisher publisher) {
        connectedPublisher = publisher;
    }
}
