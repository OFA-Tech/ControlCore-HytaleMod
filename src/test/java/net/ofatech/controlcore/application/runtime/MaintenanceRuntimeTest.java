package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceRuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;
    private PlayerInfo guest;
    private PlayerInfo vip;

    @BeforeEach
    void startUp() {
        platform.serverConfig.entries.add(new ConfigEntry(
                "MOTD",
                "General",
                "MOTD",
                "",
                ConfigValueKind.TEXT,
                List.of(),
                0,
                0,
                "Welcome",
                Optional.empty(),
                SettingAccess.EDITABLE));
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        guest = platform.players.join("Guest");
        vip = platform.players.join("Vip");
        platform.deniedNodesFor.put(guest.id(), List.of("admin", "settings", "maintenance", "maintenance.join"));
        platform.deniedNodesFor.put(vip.id(), List.of("admin", "settings", "maintenance"));
    }

    @Test
    void turningItOnKicksOnlyThoseWhoMayNotJoinAndRefusesThemAfterwards() {
        Result<Reply> on =
                platform.commands.runAsConsole("cc-maintenance", Map.of("state", "on", "message", "Back soon"));

        assertThat(on.value().text().orElseThrow()).contains("1").contains("2");
        assertThat(platform.admin.kicked).containsExactly(guest.id() + ":Back soon");
        assertThat(platform.joins.connect(guest.id(), "Guest")).contains("Back soon");
        assertThat(platform.joins.connect(vip.id(), "Vip")).isEmpty();
        assertThat(platform.joins.connect(admin.id(), "Admin")).isEmpty();

        platform.commands.runAsConsole("cc-maintenance", Map.of("state", "off"));
        assertThat(platform.joins.connect(guest.id(), "Guest")).isEmpty();
    }

    @Test
    void anEmptyMessageUsesTheDefaultText() {
        platform.commands.runAsConsole("cc-maintenance", Map.of("state", "on"));

        assertThat(platform.joins.connect(guest.id(), "Guest").orElseThrow()).contains("maintenance");
    }

    @Test
    void theMaintenanceMotdIsSwappedInAndPutBack() {
        platform.commands.runAsConsole("cc-maintenance", Map.of("state", "on", "motd", "Closed"));
        assertThat(motd()).isEqualTo("Closed");
        assertThat(platform.maintenance.state.savedMotd()).contains("Welcome");

        platform.commands.runAsConsole("cc-maintenance", Map.of("state", "off"));
        assertThat(motd()).isEqualTo("Welcome");
        assertThat(platform.maintenance.state.savedMotd()).isEmpty();
    }

    @Test
    void itTurnsItselfOffWhenTheTimeIsUp() {
        platform.commands.runAsConsole("cc-maintenance", Map.of("state", "on", "motd", "Closed", "for", "30m"));
        platform.scheduler.runAll();
        assertThat(api().maintenance().on()).isTrue();

        platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(31));
        platform.scheduler.runAll();

        assertThat(api().maintenance().on()).isFalse();
        assertThat(motd()).isEqualTo("Welcome");
    }

    @Test
    void commandsCheckTheirArguments() {
        assertThat(platform.commands
                        .runAsConsole("cc-maintenance", Map.of("state", "maybe"))
                        .error()
                        .code())
                .isEqualTo("Maintenance.BadState");
        assertThat(platform.commands
                        .runAsConsole("cc-maintenance", Map.of("state", "on", "for", "soon"))
                        .error()
                        .code())
                .isEqualTo("Maintenance.BadLength");
        assertThat(platform.commands
                        .runAsConsole("cc-maintenance", Map.of("state", "off"))
                        .error()
                        .code())
                .isEqualTo("Maintenance.AlreadyOff");
        assertThat(platform.commands
                        .runAsConsole("cc-maintenance", Map.of("state", "status"))
                        .value()
                        .text())
                .isPresent();
        assertThat(platform.commands
                        .run("cc-maintenance", guest, Map.of("state", "on"))
                        .error()
                        .code())
                .isEqualTo("Maintenance.Forbidden");
    }

    @Test
    void theApiActsWithThePlayersRights() {
        ServerApi server = api();
        Result<String> refused =
                server.actingFor(guest.id()).setMaintenance(true, ServerApi.MaintenanceOptions.DEFAULT);
        assertThat(refused.error().code()).isEqualTo("Maintenance.Forbidden");

        platform.deniedNodesFor.put(guest.id(), List.of("admin", "settings", "maintenance.join"));
        Result<String> allowed = server.actingFor(guest.id())
                .setMaintenance(true, new ServerApi.MaintenanceOptions("Later", "", Optional.of(Duration.ofHours(1))));

        assertThat(allowed.isSuccess()).isTrue();
        ServerApi.Maintenance state = server.maintenance();
        assertThat(state.on()).isTrue();
        assertThat(state.message()).isEqualTo("Later");
        assertThat(state.until()).contains(platform.clock.now.plus(Duration.ofHours(1)));
        assertThat(platform.admin.kicked).isEmpty();
    }

    @Test
    void rollingBackTurningItOnTurnsItOff() {
        api().setMaintenance(true, ServerApi.MaintenanceOptions.DEFAULT);
        HistoryApi history = ControlCoreApi.get().history();
        HistoryApi.Change change =
                history.list(Optional.of(HistoryApi.Kind.MAINTENANCE), "", 5).getFirst();

        assertThat(history.rollback(change.id()).isSuccess()).isTrue();
        assertThat(api().maintenance().on()).isFalse();
    }

    @Test
    void automationCanTurnItOnAndOff() {
        var rules = ControlCoreApi.get().rules();
        var saved = rules.save(
                Optional.empty(),
                new net.ofatech.controlcore.api.RulesApi.Form(
                        "Close",
                        "every-minutes",
                        "1",
                        "none",
                        "",
                        List.of(new net.ofatech.controlcore.api.RulesApi.Step("maintenance-on", "Closed for tonight")),
                        ""));

        assertThat(rules.test(saved.value()).isSuccess()).isTrue();
        assertThat(api().maintenance().on()).isTrue();
        assertThat(platform.admin.kicked).containsExactly(guest.id() + ":Closed for tonight");
    }

    @Test
    void onlyAdminsChangeTheMotdAndOthersAreTold() {
        platform.deniedNodesFor.put(guest.id(), List.of("admin", "settings", "maintenance.join"));

        Result<String> on = api().actingFor(guest.id())
                .setMaintenance(true, new ServerApi.MaintenanceOptions("Later", "Closed", Optional.empty()));

        assertThat(on.isSuccess()).isTrue();
        assertThat(on.value()).contains("only admins");
        assertThat(motd()).isEqualTo("Welcome");
        assertThat(platform.maintenance.state.savedMotd()).isEmpty();
    }

    @Test
    void maintenanceIsSavedBeforeTheMotdIsWritten() {
        platform.maintenance.failWrites = true;

        assertThat(api().setMaintenance(true, new ServerApi.MaintenanceOptions("", "Closed", Optional.empty()))
                        .isFailure())
                .isTrue();
        assertThat(platform.serverConfig.writes).isEmpty();
        assertThat(motd()).isEqualTo("Welcome");
    }

    @Test
    void aBrokenJoinCheckRefusesWhileMaintenanceIsOnAndAllowsWhileOff() {
        platform.permissionsFail = true;
        assertThat(platform.joins.connect(guest.id(), "Guest")).isEmpty();

        platform.permissionsFail = false;
        api().setMaintenance(true, ServerApi.MaintenanceOptions.DEFAULT);
        platform.permissionsFail = true;

        assertThat(platform.joins.connect(admin.id(), "Admin").orElseThrow()).contains("try again later");
    }

    private static ServerApi api() {
        return ControlCoreApi.get().server();
    }

    private String motd() {
        return platform.serverConfig.entry("MOTD").orElseThrow().value();
    }
}
