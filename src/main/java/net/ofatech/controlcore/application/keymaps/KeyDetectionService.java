package net.ofatech.controlcore.application.keymaps;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyCapture;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.ports.game.KeyDetections;

public final class KeyDetectionService implements KeyDetections {

    private final KeyDetectSettings settings;
    private final KeyDetector detector;
    private final KeyCapture capture;
    private final NotificationService notifications;
    private final Texts texts;
    private final Map<PlayerId, Runnable> prompts = new ConcurrentHashMap<>();

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

    @Override
    public void cancel(PlayerId player) {
        dismissPrompt(player);
        if (detector.cancel(player)) {
            capture.close(player);
        }
    }

    void forget(PlayerId player) {
        prompts.remove(player);
    }

    private void dismissPrompt(PlayerId player) {
        Runnable dismiss = prompts.remove(player);
        if (dismiss != null) {
            dismiss.run();
        }
    }

    @Override
    public KeyDetection viewedBy(PlayerId player) {
        return new KeyDetection() {
            @Override
            public boolean allKeys() {
                return settings.allKeys();
            }

            @Override
            public Mode mode() {
                return settings.mode();
            }

            @Override
            public long gameTimeoutSeconds() {
                return KeyDetector.GAME_TIMEOUT.toSeconds();
            }

            @Override
            public long screenTimeoutSeconds() {
                return KeyDetector.SCREEN_TIMEOUT.toSeconds();
            }

            @Override
            public void detect(Mode mode, Consumer<Optional<String>> done) {
                start(player, mode, done);
            }
        };
    }

    private void start(PlayerId player, KeyDetection.Mode mode, Consumer<Optional<String>> done) {
        Consumer<Optional<KeyDetector.Detected>> finished =
                result -> done.accept(result.map(KeyDetector.Detected::keys));
        Translator tr = texts.forPlayer(Optional.of(player));
        dismissPrompt(player);
        if (mode == KeyDetection.Mode.SCREEN) {
            long token = detector.fromScreen(player, result -> {
                if (result.isEmpty()) {
                    capture.close(player);
                }
                finished.accept(result);
            });
            capture.open(
                    player,
                    tr,
                    settings::allKeys,
                    keys -> detector.offer(player, token, keys.map(KeyDetector.Detected::new)));
            return;
        }
        dismissPrompt(player);
        Runnable dismiss = notifications.showToast(
                player,
                tr.t("controlcore.keys.detect.toast_title"),
                tr.t("controlcore.keys.detect.toast_text", "seconds", KeyDetector.GAME_TIMEOUT.toSeconds()),
                ToastLevel.INFO,
                (int) KeyDetector.GAME_TIMEOUT.toSeconds());
        prompts.put(player, dismiss);
        detector.fromGame(player, result -> {
            if (prompts.remove(player, dismiss)) {
                dismiss.run();
            }
            finished.accept(result);
        });
    }
}
