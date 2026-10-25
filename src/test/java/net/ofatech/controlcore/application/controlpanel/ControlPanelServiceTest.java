package net.ofatech.controlcore.application.controlpanel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.automation.ActionRegistry;
import net.ofatech.controlcore.application.automation.AutomationService;
import net.ofatech.controlcore.application.automation.AutomationSettings;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.automation.SessionService;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.diagnostics.DiagnosticsService;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.history.ChangeRecorder;
import net.ofatech.controlcore.application.history.HistoryEditor;
import net.ofatech.controlcore.application.keymaps.KeymapRegistry;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.application.settings.PermissionsEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.application.snapshot.ProfileService;
import net.ofatech.controlcore.application.snapshot.SnapshotService;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ModSettings;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.settings.PermissionsView;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.ports.game.Bans;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.Worlds;
import net.ofatech.controlcore.domain.result.ErrorType;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.fakes.FakeActiveProfileStore;
import net.ofatech.controlcore.fakes.FakeAutomationRuleStore;
import net.ofatech.controlcore.fakes.FakeChangeLogStore;
import net.ofatech.controlcore.fakes.FakeClock;
import net.ofatech.controlcore.fakes.FakeCustomKeyBindStore;
import net.ofatech.controlcore.fakes.FakeKeyBindStore;
import net.ofatech.controlcore.fakes.FakeKeyGameActionStore;
import net.ofatech.controlcore.fakes.FakeMessenger;
import net.ofatech.controlcore.fakes.FakeModCatalog;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.FakeReportWriter;
import net.ofatech.controlcore.fakes.FakeServerConfiguration;
import net.ofatech.controlcore.fakes.FakeServerHealth;
import net.ofatech.controlcore.fakes.FakeSettingPermissionStore;
import net.ofatech.controlcore.fakes.FakeSettingStore;
import net.ofatech.controlcore.fakes.FakeSettings;
import net.ofatech.controlcore.fakes.FakeSnapshotStore;
import net.ofatech.controlcore.fakes.RecordingCommandRunner;
import net.ofatech.controlcore.fakes.RecordingControlPanelView;
import net.ofatech.controlcore.fakes.RecordingLog;
import net.ofatech.controlcore.fakes.TestTexts;
import org.junit.jupiter.api.Test;

class ControlPanelServiceTest {

    private static final ServerSettings SERVER =
            new ServerSettings("Caitos Hytale Server", "WELCOME", true, 10, 5, Duration.ofMinutes(10));

    private final FakeSettings settings = new FakeSettings();
    private final FakePlayers players = new FakePlayers();
    private final RecordingControlPanelView builtIn = new RecordingControlPanelView();
    private final PlayerInfo admin = players.join("Admin");
    private SettingsRegistry registry;
    private boolean debugAllowed = true;
    private final InputProbe probe = new InputProbe() {
        @Override
        public boolean toggle(PlayerId player) {
            return true;
        }

        @Override
        public boolean isOn(PlayerId player) {
            return false;
        }

        @Override
        public Result<Void> openKeyTest(PlayerId player, String target) {
            return Result.success();
        }
    };

    private ControlPanelService service() {
        RecordingLog log = new RecordingLog();
        DomainEventBus bus = new DomainEventBus(log);
        registry = new SettingsRegistry(new FakeSettingStore(), new FakePlayerData(), bus, bus, log, new ModNames());
        var server = new FakeServerConfiguration(SERVER);
        var modNames = new ModNames();
        var keyRegistry = new KeymapRegistry(
                new FakeKeyBindStore(),
                new FakeCustomKeyBindStore(),
                new FakeKeyGameActionStore(),
                new FakePlayerData(),
                (player, node) -> true,
                log,
                ChangeRecorder.NONE);
        var policy = new SettingPermissionPolicy(
                new FakeSettingPermissionStore(), (player, node) -> true, registry, ChangeRecorder.NONE);
        var serverConfig = new ServerConfigEditor(server, Worlds.NONE, bus, bus, log);
        var mods =
                new ModsEditor(new FakeModCatalog(), registry, keyRegistry, modNames, policy, log, ChangeRecorder.NONE);
        var changes = new ChangeHistory(new FakeChangeLogStore(), new FakeClock());
        var bans = new BansService(
                Bans.NONE,
                changes,
                policy,
                (player, node) -> true,
                TestTexts.of(registry, players),
                new FakeClock(),
                registry);
        var history = new HistoryEditor(
                changes,
                registry,
                serverConfig,
                keyRegistry,
                policy,
                mods,
                (player, node) -> true,
                TestTexts.of(registry, players),
                bans,
                new FakeClock());
        var snapshots = new SnapshotService(
                new FakeSnapshotStore(),
                registry,
                serverConfig,
                keyRegistry,
                new FakeCustomKeyBindStore(),
                new FakeSettingPermissionStore(),
                policy,
                mods,
                new FakePlayerData(),
                players,
                (player, node) -> true,
                history,
                TestTexts.of(registry, players),
                new FakeClock(),
                log);
        var profiles = new ProfileService(
                snapshots, serverConfig, new FakeActiveProfileStore(), TestTexts.of(registry, players), log);
        var automation = new AutomationService(
                new FakeAutomationRuleStore(),
                new AutomationSettings(registry),
                new ActionRegistry(),
                new SessionService(new FakePlayerData(), players, new FakeClock()),
                new NotificationService(new FakeMessenger(), players, policy),
                new RecordingCommandRunner(),
                profiles,
                registry,
                serverConfig,
                policy,
                (player, node) -> true,
                players,
                new FakeServerHealth(),
                new FakeClock(),
                TestTexts.of(registry, players),
                log);
        return new ControlPanelService(
                new ControlPanelSettings(registry, settings),
                players,
                server,
                builtIn,
                registry,
                serverConfig,
                bans,
                new KeymapsEditor(keyRegistry, TestTexts.of(registry, players), modNames),
                policy,
                TestTexts.of(registry, players),
                mods,
                new PermissionsEditor(registry, serverConfig, policy),
                probe,
                (player, node) -> !node.equals("debug") || debugAllowed,
                history,
                snapshots,
                profiles,
                new DiagnosticsService(
                        new FakeServerHealth(),
                        List::of,
                        new FakeReportWriter(),
                        mods,
                        policy,
                        TestTexts.of(registry, players),
                        new FakeClock(),
                        log),
                automation);
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
    void adminsGetThePermissionRulesAndTheDebugToolsTheyMayUse() {
        var service = service();

        service.open(admin.id(), ControlPanelRequest.ALL);

        PermissionsView rules = builtIn.lastContent.permissions().orElseThrow();
        assertThat(rules.list())
                .extracting(PermissionsView.Entry::target)
                .contains(
                        "mod:server",
                        "setting:server:server-name",
                        "mod:controlcore",
                        "setting:controlcore:panel-start-tab");
        assertThat(rules.save("setting:server:server-name", "", "staff.name").isSuccess())
                .isTrue();
        assertThat(rules.list())
                .filteredOn(entry -> entry.target().equals("setting:server:server-name"))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.edit()).isEqualTo("staff.name");
                    assertThat(entry.view()).isEmpty();
                    assertThat(entry.adminRule()).isTrue();
                    assertThat(entry.description()).contains("staff.name");
                });
        assertThat(rules.reset("setting:server:server-name").isSuccess()).isTrue();
        assertThat(rules.list())
                .filteredOn(entry -> entry.target().equals("setting:server:server-name"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.adminRule()).isFalse());
        assertThat(builtIn.lastContent.debug()).isPresent();
    }

    @Test
    void debugToolsNeedTheDebugPermission() {
        debugAllowed = false;
        var service = service();

        service.open(admin.id(), ControlPanelRequest.ALL);

        assertThat(builtIn.lastContent.debug()).isEmpty();
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
