package net.ofatech.controlcore.application.keymaps;

import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyCapture;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.ports.game.KeyDetections;

public final class KeyDetectionService implements KeyDetections {

    private static final int TELL_SECONDS = 8;

    private final KeyDetectSettings settings;
    private final KeyDetector detector;
    private final KeyCapture capture;
    private final NotificationService notifications;
    private final Texts texts;
    private final SettingPermissionPolicy permissions;

    public KeyDetectionService(
            KeyDetectSettings settings,
            KeyDetector detector,
            KeyCapture capture,
            NotificationService notifications,
            Texts texts,
            SettingPermissionPolicy permissions) {
        this.settings = settings;
        this.detector = detector;
        this.capture = capture;
        this.notifications = notifications;
        this.texts = texts;
        this.permissions = permissions;
    }

    @Override
    public KeyDetection viewedBy(PlayerId player) {
        boolean adminWhenBuilt = permissions.isAdmin(player);
        return new KeyDetection() {
            @Override
            public boolean allKeys() {
                return settings.allKeys();
            }

            @Override
            public Mode mode() {
                return settings.mode() == KeyDetectSettings.DetectMode.SCREEN ? Mode.SCREEN : Mode.GAME;
            }

            @Override
            public Optional<Mode> detect(Consumer<Optional<String>> done) {
                Mode mode = mode();
                if (mode == Mode.SCREEN && detector.pending(player)) {
                    return Optional.empty();
                }
                start(player, mode, done);
                return Optional.of(mode);
            }

            @Override
            public boolean stillAllowed() {
                return permissions.isAdmin(player) == adminWhenBuilt;
            }

            @Override
            public void tell(String title, String text) {
                notifications.toast(player, title, text, ToastLevel.INFO, TELL_SECONDS);
            }
        };
    }

    private void start(PlayerId player, KeyDetection.Mode mode, Consumer<Optional<String>> done) {
        Consumer<Optional<KeyDetector.Detected>> finished =
                result -> done.accept(result.map(KeyDetector.Detected::keys));
        Translator tr = texts.forPlayer(Optional.of(player));
        if (mode == KeyDetection.Mode.SCREEN) {
            long token = detector.fromScreen(player, finished);
            capture.open(player, tr, keys -> detector.offer(player, token, keys.map(KeyDetector.Detected::new)));
            return;
        }
        detector.fromGame(player, finished);
        notifications.toast(
                player,
                tr.t("controlcore.keys.detect.toast_title"),
                tr.t("controlcore.keys.detect.toast_text", "seconds", KeyDetector.TIMEOUT.toSeconds()),
                ToastLevel.INFO,
                (int) KeyDetector.TIMEOUT.toSeconds());
    }
}
