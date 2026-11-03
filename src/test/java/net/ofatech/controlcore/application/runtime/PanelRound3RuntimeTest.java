package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.DefaultsApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanelRound3RuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
    }

    @Test
    void togglingARuleThatCouldNotBeSavedIsReportedAndNotKept() {
        save(form("Ping", "join", "", "none", "", "message-all", "ping", ""));
        platform.automationRules.failWrites = true;

        Result<Reply> toggled = platform.commands.run("cc-rule-toggle", admin, Map.of("rule", "ping"));

        assertThat(toggled.isFailure()).as("%s", toggled).isTrue();
        assertThat(toggled.error().code()).isEqualTo("Storage.NotSaved");
        assertThat(platform.automationRules.rules)
                .singleElement()
                .extracting(Rule::enabled)
                .isEqualTo(true);
    }

    @Test
    void aToastTextOfExactlyTheHudLimitIsAccepted() {
        Result<String> saved =
                view().save(Optional.empty(), form("Edge", "join", "", "none", "", "toast-all", "x".repeat(200), ""));

        assertThat(saved.isSuccess()).as("%s", saved).isTrue();
    }

    @Test
    void aPermissionRuleThatCouldNotBeSavedLeavesNoHistoryEntry() {
        platform.settingPermissions.failWrites = true;

        Result<Reply> set = platform.commands.runAsConsole(
                "cc-perm", Map.of("target", "controlcore:panel-start-tab", "side", "edit", "node", "staff.tab"));

        assertThat(set.error().code()).isEqualTo("Storage.NotSaved");
        assertThat(ControlCoreApi.get().history().list(Optional.of(HistoryApi.Kind.PERMISSION), "", 0))
                .isEmpty();
    }

    @Test
    void theInventoryApiRefusesAModeratorWithoutTheInventoryNode() {
        PlayerInfo target = platform.players.join("Builder");
        PlayerInfo looker = platform.players.join("Looker");
        PlayerInfo peeker = platform.players.join("Peeker");
        platform.deniedNodesFor.put(target.id(), List.of("settings", "admin"));
        platform.deniedNodesFor.put(looker.id(), List.of("settings", "admin"));
        platform.deniedNodesFor.put(peeker.id(), List.of("settings", "admin", "players.inventory"));
        platform.admin.inventories.put(target.id(), List.of(new PlayerAdmin.Item("hotbar", 0, "Rock", 3)));
        var players = ControlCoreApi.get().players();

        var allowed = players.actingFor(looker.id()).inventory("Builder").join();
        var refused = players.actingFor(peeker.id()).inventory("Builder").join();

        assertThat(allowed.isSuccess()).as("%s", allowed).isTrue();
        assertThat(refused.isFailure()).as("%s", refused).isTrue();
        assertThat(refused.error().code()).isEqualTo("Players.Forbidden");
    }

    @Test
    void restoringServerKeysWithASearchFindsTheExtraKeysLikeThePanel() {
        ok(platform.commands.runAsConsole(
                "cc-keybind-add-server", Map.of("name", "warp", "keys", "ctrl+r", "command", "say warp")));
        ok(platform.commands.runAsConsole(
                "cc-keybind-add-server", Map.of("name", "home", "keys", "ctrl+y", "command", "say home")));
        ok(platform.commands.runAsConsole("cc-bind-server", Map.of("id", "custom:warp", "keys", "CTRL + T | ALT + 7")));
        ok(platform.commands.runAsConsole(
                "cc-bind-server", Map.of("id", "custom:home", "keys", "CTRL + U | SHIFT + 9")));

        assertThat(ControlCoreApi.get().defaults().count(DefaultsApi.Section.SERVER_KEYS, Optional.empty(), "desc:alt"))
                .isEqualTo(1);
    }

    private static void ok(Result<Reply> result) {
        assertThat(result.isSuccess()).as("%s", result).isTrue();
    }

    private void save(AutomationView.Form form) {
        Result<String> saved = view().save(Optional.empty(), form);
        assertThat(saved.isSuccess()).as(saved.toString()).isTrue();
    }

    private AutomationView view() {
        platform.commands.run("open-admin-config", admin, Map.of());
        return platform.controlPanel.lastContent.automation().orElseThrow();
    }

    private static AutomationView.Form form(String... values) {
        List<String> v = List.of(values);
        List<AutomationView.Step> steps = new ArrayList<>();
        for (int i = 5; i + 1 < v.size() - 1; i += 2) {
            steps.add(new AutomationView.Step(v.get(i), v.get(i + 1)));
        }
        return new AutomationView.Form(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4), steps, v.getLast());
    }
}
