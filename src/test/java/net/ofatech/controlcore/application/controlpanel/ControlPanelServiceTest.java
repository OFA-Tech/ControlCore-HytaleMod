package net.ofatech.controlcore.application.controlpanel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.application.keymaps.KeymapRegistry;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ModSettings;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.result.ErrorType;
import net.ofatech.controlcore.fakes.FakeKeyBindStore;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.FakeServerConfiguration;
import net.ofatech.controlcore.fakes.FakeSettingStore;
import net.ofatech.controlcore.fakes.FakeSettings;
import net.ofatech.controlcore.fakes.RecordingControlPanelView;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;

class ControlPanelServiceTest {

    private static final ServerSettings SERVER =
            new ServerSettings("Caitos Hytale Server", "WELCOME", true, 10, 5, Duration.ofMinutes(10));

    private final FakeSettings settings = new FakeSettings();
    private final FakePlayers players = new FakePlayers();
    private final RecordingControlPanelView builtIn = new RecordingControlPanelView();
    private final PlayerInfo admin = players.join("Admin");
    private SettingsRegistry registry;

    private ControlPanelService service() {
        RecordingLog log = new RecordingLog();
        DomainEventBus bus = new DomainEventBus(log);
        registry = new SettingsRegistry(
                new FakeSettingStore(),
                new FakePlayerData(),
                bus,
                bus,
                log,
                new net.ofatech.controlcore.application.settings.ModNames());
        var server = new FakeServerConfiguration(SERVER);
        var modNames = new net.ofatech.controlcore.application.settings.ModNames();
        var keyRegistry = new KeymapRegistry(
                new FakeKeyBindStore(),
                new net.ofatech.controlcore.fakes.FakeCustomKeyBindStore(),
                new FakePlayerData(),
                (player, node) -> true,
                log);
        var policy = new SettingPermissionPolicy(
                new net.ofatech.controlcore.fakes.FakeSettingPermissionStore(), (player, node) -> true, registry);
        return new ControlPanelService(
                new ControlPanelSettings(registry, settings),
                players,
                server,
                builtIn,
                registry,
                new ServerConfigEditor(server, bus, bus, log),
                new KeymapsEditor(keyRegistry, net.ofatech.controlcore.fakes.TestTexts.of(registry, players), modNames),
                policy,
                net.ofatech.controlcore.fakes.TestTexts.of(registry, players),
                new net.ofatech.controlcore.application.mods.ModsEditor(
                        new net.ofatech.controlcore.fakes.FakeModCatalog(),
                        registry,
                        keyRegistry,
                        modNames,
                        policy,
                        log));
    }

    @Test
    void theSwitchCanBeChangedLiveThroughTheRegistry() {
        var service = service();

        registry.write(SettingId.of(ControlPanelSettings.ENABLED_ID), Optional.empty(), "false");

        assertThat(service.isAvailable()).isFalse();
        assertThat(service.open(admin.id(), ControlPanelRequest.ALL).error().code())
                .isEqualTo("ControlPanel.Disabled");
    }

    @Test
    void usesTheBuiltInViewWithTheServerSettings() {
        var service = service();

        var result = service.open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.isSuccess()).isTrue();
        assertThat(builtIn.opened).containsExactly(new RecordingControlPanelView.Opened(admin.id(), SERVER));
        assertThat(builtIn.lastContent.settings().snapshot(SettingScope.SERVER, Optional.empty()))
                .as("an admin sees every server setting")
                .isEqualTo(registry.snapshot(SettingScope.SERVER, Optional.empty()));
        assertThat(builtIn.lastContent.manageKeys()).isTrue();
        assertThat(builtIn.lastContent.serverConfig().snapshot(SettingScope.SERVER, Optional.empty()))
                .extracting(snapshot -> snapshot.id().toString())
                .contains("server:server-name", "server:connection-timeouts.play-timeout", "server:ban-storage");
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void respectsTheSettingsSwitch() {
        settings.settings = new ModSettings(false);
        var service = service();

        var result = service.open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.error().code()).isEqualTo("ControlPanel.Disabled");
        assertThat(service.isAvailable()).isFalse();
        assertThat(builtIn.opened).isEmpty();
    }

    @Test
    void preferencesStayAvailableWhenThePanelIsDisabled() {
        settings.settings = new ModSettings(false);
        var service = service();

        var result = service.open(admin.id(), ControlPanelRequest.PLAYER_PREFERENCES);

        assertThat(result.isSuccess()).isTrue();
        assertThat(builtIn.lastRequest).isEqualTo(ControlPanelRequest.PLAYER_PREFERENCES);
    }

    @Test
    void rejectsRequestsWithoutSections() {
        var result = service().open(admin.id(), new ControlPanelRequest(false, false, false));

        assertThat(result.error().code()).isEqualTo("ControlPanel.NothingToShow");
        assertThat(builtIn.opened).isEmpty();
    }

    @Test
    void rejectsPlayersWhoAreNotOnline() {
        var result = service().open(PlayerId.of(UUID.randomUUID()), ControlPanelRequest.ALL);

        assertThat(result.error().type()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(builtIn.opened).isEmpty();
    }
}
