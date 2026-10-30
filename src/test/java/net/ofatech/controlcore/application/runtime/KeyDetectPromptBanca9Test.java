package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyDetectPromptBanca9Test extends RuntimeTestBase {

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
        platform.connectedPublisher.publish(new PlayerJoined(admin));
    }

    @Test
    void thePromptGoesAwayWhenTheDetectionExpires() {
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(KeyDetection.Mode.GAME, results::add);
        assertThat(overlay().toasts()).hasSize(1);

        platform.scheduler.runAll();

        assertThat(results).containsExactly(Optional.empty());
        assertThat(overlay().toasts()).isEmpty();
    }

    @Test
    void detectingTwiceInARowShowsOnlyOnePrompt() {
        KeyDetection detection = detection();
        detection.detect(KeyDetection.Mode.GAME, result -> {});
        detection.detect(KeyDetection.Mode.GAME, result -> {});

        assertThat(overlay().toasts()).hasSize(1);
    }

    @Test
    void thePromptGoesAwayButOtherToastsStay() {
        NotificationService notifications = service();
        notifications.toast(admin.id(), "", "other", ToastLevel.INFO, 30);
        detection().detect(KeyDetection.Mode.GAME, result -> {});
        assertThat(overlay().toasts()).hasSize(2);

        platform.input.press(admin.id(), KeySignal.USE);

        assertThat(overlay().toasts()).extracting(Overlay.Line::text).containsExactly("other");
    }

    @Test
    void reopeningThePanelAfterTheDetectionEndedIsHarmless() {
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(KeyDetection.Mode.GAME, results::add);
        platform.input.press(admin.id(), KeySignal.USE);

        detection();
        detection();

        assertThat(results).containsExactly(Optional.of("F"));
        assertThat(overlay().toasts()).isEmpty();
    }

    @Test
    void withHytaleToastsThePromptGoesThereAndDetectionStillWorks() {
        platform.commands.run(
                "cc-pref", admin, Map.of("id", "controlcore:toast-position", "value", "Hytale notifications"));
        List<Optional<String>> results = new ArrayList<>();
        detection().detect(KeyDetection.Mode.GAME, results::add);

        assertThat(platform.overlay.hytaleToasts).hasSize(1);
        platform.input.press(admin.id(), KeySignal.USE);

        assertThat(results).containsExactly(Optional.of("F"));
    }

    @Test
    void showToastWithHytalePositionGivesANoOpDismiss() {
        platform.commands.run(
                "cc-pref", admin, Map.of("id", "controlcore:toast-position", "value", "Hytale notifications"));

        Runnable dismiss = service().showToast(admin.id(), "T", "x", ToastLevel.INFO, 5);

        assertThat(dismiss).isNotNull();
        dismiss.run();
        dismiss.run();
        assertThat(platform.overlay.hytaleToasts).containsExactly("info:x");
    }

    @Test
    void showToastForNobodyOrHiddenToastsGivesANoOpDismiss() {
        NotificationService notifications = service();

        Runnable none = notifications.showToast(null, "T", "x", ToastLevel.INFO, 5);
        platform.commands.run("cc-pref", admin, Map.of("id", "controlcore:hud.toasts", "value", "false"));
        Runnable hidden = notifications.showToast(admin.id(), "T", "x", ToastLevel.INFO, 5);

        none.run();
        hidden.run();
        Overlay shown = platform.overlay.shown.get(admin.id());
        assertThat(shown == null || shown.toasts().isEmpty()).isTrue();
    }

    @Test
    void dismissRemovesOnlyItsOwnToastAndIsSafeAfterExpiry() {
        NotificationService notifications = service();
        Runnable first = notifications.showToast(admin.id(), "", "one", ToastLevel.INFO, 5);
        notifications.showToast(admin.id(), "", "two", ToastLevel.INFO, 5);

        first.run();
        assertThat(overlay().toasts()).extracting(Overlay.Line::text).containsExactly("two");

        platform.scheduler.runAll();
        first.run();
        assertThat(overlay().toasts()).isEmpty();
    }

    @Test
    void theCaptureScreenGetsTheAllKeysChoice() {
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        detection().detect(KeyDetection.Mode.SCREEN, result -> {});
        platform.keyCapture.press(admin.id(), null);

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:all-keys-in-lists", "value", "true"));
        detection().detect(KeyDetection.Mode.SCREEN, result -> {});

        assertThat(platform.keyCapture.anyKey).containsExactly(false, true);
    }

    private NotificationService service() {
        assertThat(ControlCoreApi.get().notifications()).isInstanceOf(NotificationService.class);
        return (NotificationService) ControlCoreApi.get().notifications();
    }

    private Overlay overlay() {
        return platform.overlay.shown.get(admin.id());
    }

    private KeyDetection detection() {
        platform.commands.run(COMMAND, admin, Map.of());
        return platform.controlPanel.lastContent.keyDetection();
    }
}
