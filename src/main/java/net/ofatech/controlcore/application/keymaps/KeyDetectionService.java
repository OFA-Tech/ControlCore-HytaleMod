package net.ofatech.controlcore.application.keymaps;

import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.keys.KeyDetection;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyCapture;

public final class KeyDetectionService {

    private final KeyDetectSettings settings;
    private final KeyDetector detector;
    private final KeyCapture capture;
    private final NotificationService notifications;
    private final Texts texts;

    public KeyDetectionService(
            KeyDetectSettings settings,
            KeyDetector detector,
            KeyCapture capture,
            NotificationService notifications,
            Texts texts) {
        this.settings = settings;
        this.detector = detector;
        this.capture = capture;
        this.notifications = notifications;
        this.texts = texts;
    }

    public static KeyDetectionService none() {
        return new KeyDetectionService(null, null, null, null, null);
    }

    public KeyDetection viewedBy(PlayerId player) {
        if (settings == null) {
            return KeyDetection.NONE;
        }
        return new KeyDetection() {
            @Override
            public boolean allKeys() {
                return settings.allKeys();
            }

            @Override
            public boolean fromScreen() {
                return settings.mode() == KeyDetectSettings.DetectMode.SCREEN;
            }

            @Override
            public void detect(Consumer<Optional<String>> done) {
                start(player, fromScreen(), done);
            }
        };
    }

    private void start(PlayerId player, boolean fromScreen, Consumer<Optional<String>> done) {
        Consumer<Optional<KeyDetector.Detected>> finished =
                result -> done.accept(result.map(KeyDetector.Detected::keys));
        if (fromScreen) {
            detector.fromScreen(player, finished);
            capture.open(player, keys -> detector.offer(player, keys.map(KeyDetector.Detected::new)));
            return;
        }
        detector.fromGame(player, finished);
        Translator tr = texts.forPlayer(Optional.of(player));
        notifications.toast(
                player,
                tr.t("controlcore.keys.detect.toast_title"),
                tr.t("controlcore.keys.detect.toast_text", "seconds", KeyDetector.TIMEOUT.toSeconds()),
                ToastLevel.INFO,
                (int) KeyDetector.TIMEOUT.toSeconds());
    }
}
