package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanelParityRuntimeTest extends RuntimeTestBase {

    private static final String ADMIN_GROUP = "hytale:Admin";
    private static final String ADVENTURER = "hytale:Adventurer";

    private PlayerInfo admin;
    private PlayerInfo other;
    private PlayerInfo builder;

    @BeforeEach
    void startUp() {
        platform.serverConfig.entries.add(new ConfigEntry(
                "RequireJoinPermission",
                "General",
                "Require join permission",
                "",
                ConfigValueKind.BOOLEAN,
                List.of(),
                0,
                0,
                "false",
                Optional.of("false"),
                SettingAccess.EDITABLE));
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        other = platform.players.join("Other");
        builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin", "players.whitelist"));
        platform.connectedPublisher.publish(new PlayerJoined(admin));
        platform.connectedPublisher.publish(new PlayerJoined(builder));
    }

    private static String text(Result<Reply> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
        return result.value().text().orElseThrow();
    }

    private static String code(Result<?> result) {
        assertThat(result.isFailure()).as("%s", result).isTrue();
        return result.error().code();
    }

    private String required() {
        return platform.serverConfig
                .entry("RequireJoinPermission")
                .orElseThrow()
                .value();
    }

    private PlayersView players(PlayerInfo viewer) {
        platform.commands.run(COMMAND, viewer, Map.of());
        return platform.controlPanel.lastContent.players().orElseThrow();
    }

    @Test
    void requiringTheWhitelistGoesThroughTheServerConfigAndCanBeRolledBack() {
        assertThat(text(platform.commands.runAsConsole("cc-whitelist-require", Map.of("state", "on"))))
                .contains("required");
        assertThat(required()).isEqualTo("true");
        ChangeRecord record = platform.changeLog.records.getLast();
        assertThat(record.kind()).isEqualTo(ChangeRecord.Kind.SERVER_CONFIG);
        assertThat(record.target()).isEqualTo("server:require-join-permission");
        assertThat(record.newValue()).isEqualTo("true");
        assertThat(code(platform.commands.runAsConsole("cc-whitelist-require", Map.of("state", "on"))))
                .isEqualTo("Players.NothingToDo");
        assertThat(code(platform.commands.runAsConsole("cc-whitelist-require", Map.of("state", "maybe"))))
                .isEqualTo("Players.BadChoice");

        PlayersView view = players(admin);
        assertThat(view.whitelistRequired()).isTrue();
        assertThat(view.requireWhitelist(false).isSuccess()).isTrue();
        assertThat(required()).isEqualTo("false");

        long id = platform.changeLog.records.getLast().id();
        assertThat(platform.commands
                        .run("cc-rollback", admin, Map.of("change", (int) id))
                        .isSuccess())
                .isTrue();
        assertThat(required()).isEqualTo("true");
    }

    @Test
    void onlyPlayersWithTheWhitelistRightMayRequireIt() {
        assertThat(code(platform.commands.run("cc-whitelist-require", builder, Map.of("state", "on"))))
                .isEqualTo("Players.Forbidden");
        assertThat(required()).isEqualTo("false");
    }

    @Test
    void requiringTheWhitelistAlsoFollowsTheKeysEditRule() {
        platform.deniedNodesFor.put(other.id(), List.of("settings", "admin"));

        assertThat(platform.commands
                        .run("cc-whitelist-require", other, Map.of("state", "on"))
                        .isFailure())
                .isTrue();
        assertThat(required()).isEqualTo("false");

        assertThat(ControlCoreApi.get()
                        .permissions()
                        .set("server:require-join-permission", "edit", "everyone")
                        .isSuccess())
                .isTrue();
        assertThat(text(platform.commands.run("cc-whitelist-require", other, Map.of("state", "on"))))
                .contains("required");
        assertThat(required()).isEqualTo("true");
    }

    @Test
    void setAsOnlyGroupLeavesExactlyThatGroupAndRecordsEachStep() {
        platform.access.addToGroup(builder.id(), ADMIN_GROUP);
        platform.access.addToGroup(builder.id(), "hytale:Builder");
        int before = platform.changeLog.records.size();

        assertThat(text(platform.commands.runAsConsole(
                        "cc-group", Map.of("action", "set", "player", "Builder", "group", ADVENTURER))))
                .contains("Builder");

        assertThat(platform.access.groupsOf(builder.id())).containsExactly(ADVENTURER);
        List<ChangeRecord> steps = platform.changeLog.records.subList(before, platform.changeLog.records.size());
        assertThat(steps).hasSize(3).allMatch(step -> step.kind() == ChangeRecord.Kind.PLAYER);
        assertThat(code(platform.commands.runAsConsole(
                        "cc-group",
                        Map.of("action", "set", "player", "Builder", "group", ADVENTURER.toUpperCase(Locale.ROOT)))))
                .isEqualTo("Players.NothingToDo");

        long removedAdmin = steps.stream()
                .filter(step ->
                        step.target().endsWith(ADMIN_GROUP) && step.newValue().equals("false"))
                .findFirst()
                .orElseThrow()
                .id();
        assertThat(platform.commands
                        .run("cc-rollback", admin, Map.of("change", (int) removedAdmin))
                        .isSuccess())
                .isTrue();
        assertThat(platform.access.groupsOf(builder.id())).containsExactlyInAnyOrder(ADVENTURER, ADMIN_GROUP);
    }

    @Test
    void setAsOnlyGroupKeepsTheOperatorRules() {
        platform.access.addToGroup(admin.id(), ADMIN_GROUP);
        platform.access.addToGroup(other.id(), ADMIN_GROUP);

        assertThat(code(platform.commands.run(
                        "cc-group", admin, Map.of("action", "set", "player", "Admin", "group", ADVENTURER))))
                .isEqualTo("Players.Self");
        assertThat(platform.access.groupsOf(admin.id())).containsExactly(ADMIN_GROUP);
        assertThat(code(platform.commands.run(
                        "cc-group", builder, Map.of("action", "set", "player", "Other", "group", ADVENTURER))))
                .isEqualTo("Players.AdminsOnly");

        PlayersView view = players(admin);
        assertThat(PlayerAction.allowedBy(view.rights())).contains(PlayerAction.GROUP_SET);
        Result<String> done = view.apply(
                        PlayerAction.GROUP_SET, new PlayerAction.Input("Other", "", "", ADVENTURER, ""))
                .join();
        assertThat(done.isSuccess()).as("%s", done).isTrue();
        assertThat(platform.access.groupsOf(other.id())).isEqualTo(Set.of(ADVENTURER));
        assertThat(code(platform.commands.runAsConsole(
                        "cc-group", Map.of("action", "maybe", "player", "Other", "group", ADVENTURER))))
                .isEqualTo("Players.BadChoice");
    }

    @Test
    void thePanelSendsToastsAndWarningsTheSameWayAsTheCommands() {
        platform.commands.run(COMMAND, admin, Map.of());
        AutomationView automation =
                platform.controlPanel.lastContent.automation().orElseThrow();

        assertThat(automation.noticeTargets())
                .extracting(AutomationView.Choice::id)
                .contains("everyone", "admins", builder.id().value().toString());
        assertThat(automation
                        .send(new AutomationView.Notice(
                                builder.id().value().toString(), "toast", "success", "", "Hi|there"))
                        .value())
                .contains("Builder");
        assertThat(overlay(builder).toasts()).extracting(Overlay.Line::text).containsExactly("there");

        assertThat(automation
                        .send(new AutomationView.Notice("everyone", "warning", "error", "0", "Restart soon"))
                        .isSuccess())
                .isTrue();
        assertThat(overlay(builder).warnings()).extracting(Overlay.Line::text).containsExactly("Restart soon");
        assertThat(automation
                        .send(new AutomationView.Notice("everyone", "clear", "", "", ""))
                        .isSuccess())
                .isTrue();
        assertThat(overlay(builder).warnings()).isEmpty();

        assertThat(code(automation.send(new AutomationView.Notice("everyone", "warning", "info", "-5", "x"))))
                .isEqualTo("Notify.InvalidSeconds");
        assertThat(code(automation.send(new AutomationView.Notice("everyone", "toast", "loud", "", "x"))))
                .isEqualTo("Notify.BadStyle");
        assertThat(code(automation.send(new AutomationView.Notice("everyone", "toast", "info", "", " "))))
                .isEqualTo("Notify.InvalidText");
        assertThat(code(automation.send(new AutomationView.Notice("everyone", "shout", "info", "", "x"))))
                .isEqualTo("Notify.BadKind");
    }

    @Test
    void playerPrefsCarryALiveHideHudToggle() {
        platform.commands.run("prefs", builder, Map.of());
        var prefs = platform.controlPanel.lastContent.settings();
        SettingId hidden = SettingId.of("controlcore:hud-hidden");

        List<SettingSnapshot> rows = prefs.snapshot(SettingScope.PLAYER, Optional.of(builder.id()));
        int at = rows.stream().map(SettingSnapshot::id).toList().indexOf(hidden);
        assertThat(at).isNotNegative();
        assertThat(rows.get(at + 1).id().key()).startsWith("hud.");
        assertThat(rows.get(at).definition().heading())
                .isEqualTo(rows.get(at + 1).definition().heading());

        assertThat(prefs.write(hidden, Optional.of(builder.id()), "true").isSuccess())
                .isTrue();
        assertThat(ControlCoreApi.get().huds().isHidden(builder.id())).isTrue();
        assertThat(prefs.read(hidden, Optional.of(builder.id())).value().value())
                .isEqualTo("true");

        ControlCoreApi.get().huds().setHidden(builder.id(), false);
        assertThat(prefs.read(hidden, Optional.of(builder.id())).value().value())
                .isEqualTo("false");
        assertThat(prefs.snapshot(SettingScope.SERVER, Optional.empty()))
                .extracting(SettingSnapshot::id)
                .doesNotContain(hidden);
    }

    private Overlay overlay(PlayerInfo player) {
        return platform.overlay.shown.get(player.id());
    }
}
