package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.ports.game.PlayerContext;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationsRuntimeTest extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
    }

    @Test
    void toastsStackUpToThreeAndExpire() {
        var notifications = ControlCoreApi.get().notifications();
        for (int i = 1; i <= 4; i++) {
            notifications.toast(admin.id(), "", "toast " + i, ToastLevel.SUCCESS, 5);
        }

        assertThat(overlay().toasts()).extracting(Overlay.Line::text).containsExactly("toast 2", "toast 3", "toast 4");
        assertThat(overlay().toastCorner()).isEqualTo(Overlay.TOP_RIGHT);

        platform.scheduler.runAll();
        assertThat(overlay().toasts()).isEmpty();
    }

    @Test
    void playersChooseWhereToastsAndWarningsGo() {
        pref("controlcore:toast-position", "Hytale notifications");
        pref("controlcore:warning-position", "Above the hotbar");
        var notifications = ControlCoreApi.get().notifications();

        notifications.toast(admin.id(), "Saved", "done", ToastLevel.INFO, 5);
        notifications.warning(admin.id(), "pvp", "PvP is on", ToastLevel.ERROR, 0);

        assertThat(platform.overlay.hytaleToasts).containsExactly("info:done");
        assertThat(overlay().toasts()).isEmpty();
        assertThat(overlay().warningPlace()).isEqualTo(Overlay.BOTTOM);
        assertThat(overlay().warnings()).extracting(Overlay.Line::text).containsExactly("PvP is on");
    }

    @Test
    void warningsForEveryoneReachLateJoinersUntilCleared() {
        var notifications = ControlCoreApi.get().notifications();
        notifications.warningAll("restart", "Restart in 10 minutes", ToastLevel.WARNING, 0);
        PlayerInfo bob = platform.players.join("Bob");
        platform.connectedPublisher.publish(new PlayerJoined(bob));

        assertThat(platform.overlay.shown.get(bob.id()).warnings())
                .extracting(Overlay.Line::text)
                .containsExactly("Restart in 10 minutes");

        notifications.clearWarningAll("restart");
        assertThat(overlay().warnings()).isEmpty();
        assertThat(platform.overlay.shown.get(bob.id()).warnings()).isEmpty();
    }

    @Test
    void turningTheHudElementOffHidesEverything() {
        pref("controlcore:hud.toasts", "false");

        ControlCoreApi.get().notifications().toast(admin.id(), "", "hidden", ToastLevel.INFO, 5);

        assertThat(overlay().empty()).isTrue();
    }

    @Test
    void situationRulesHideHudPartsByWorldModeAndCombat() {
        run(
                "cc-set",
                Map.of("id", "controlcore:hud-context-rules", "value", "compass hide combat, chat hide world:lobby"));
        var hud = ControlCoreApi.get().huds();

        assertThat(hud.isVisible(admin.id(), HudPart.COMPASS)).isTrue();
        platform.context.put(admin.id(), new PlayerContext.Context("lobby", "adventure", true));

        assertThat(hud.isVisible(admin.id(), HudPart.COMPASS)).isFalse();
        assertThat(hud.isVisible(admin.id(), HudPart.CHAT)).isFalse();
        assertThat(hud.isVisible(admin.id(), HudPart.HOTBAR)).isTrue();
    }

    @Test
    void rulesShowToastsWarningsAndReactToCombat() {
        save(form("Fight", "combat-enter", "", "none", "", "toast-player", "error: Combat | You are in combat", ""));
        platform.context.put(admin.id(), new PlayerContext.Context("default", "adventure", false));
        platform.scheduler.runAll();
        platform.context.put(admin.id(), new PlayerContext.Context("default", "adventure", true));
        platform.scheduler.runAll();

        assertThat(platform.overlay.everToasted).containsExactly("error:You are in combat");
    }

    @Test
    void leveledActionValuesAreParsed() {
        save(form("Clear", "join", "", "none", "", "clear-warning", "all", ""));
        Result<String> bad =
                view().save(Optional.empty(), form("Bad", "join", "", "none", "", "clear-warning", "Not ok!", ""));

        assertThat(bad.isFailure()).isTrue();
    }

    private Overlay overlay() {
        return platform.overlay.shown.get(admin.id());
    }

    private void pref(String id, String value) {
        run("cc-pref", Map.of("id", id, "value", value));
    }

    private void run(String command, Map<String, Object> args) {
        platform.commands.run(command, admin, args);
    }

    private void save(AutomationView.Form form) {
        Result<String> saved = view().save(Optional.empty(), form);
        assertThat(saved.isSuccess()).as(saved.toString()).isTrue();
    }

    private AutomationView view() {
        run("open-admin-config", Map.of());
        return platform.controlPanel.lastContent.automation().orElseThrow();
    }

    private static AutomationView.Form form(String... values) {
        List<String> v = List.of(values);
        List<AutomationView.Step> steps = new java.util.ArrayList<>();
        for (int i = 5; i + 1 < v.size(); i += 2) {
            steps.add(new AutomationView.Step(v.get(i), v.get(i + 1)));
        }
        return new AutomationView.Form(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4), steps, v.getLast());
    }
}
