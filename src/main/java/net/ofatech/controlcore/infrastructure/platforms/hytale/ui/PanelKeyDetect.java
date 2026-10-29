package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.model.PanelRights;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.infrastructure.platforms.hytale.input.HytaleKeyCapture;
import net.ofatech.controlcore.infrastructure.platforms.hytale.pages.HytalePages;

final class PanelKeyDetect {

    private static final String FOOTER = "#FooterStatus.Text";

    enum Target {
        OWN_KEY,
        SERVER_KEY,
        BIND_FORM
    }

    interface Host {
        PlayerRef viewer();

        CustomUIPage page();

        void closePanel();

        void send(UICommandBuilder update);

        String asViewer(Supplier<String> work);
    }

    private final KeyDetection detection;
    private final Supplier<PanelRights> rights;
    private final Translator tr;
    private final Host host;
    private final Log log;

    PanelKeyDetect(KeyDetection detection, Supplier<PanelRights> rights, Translator tr, Host host, Log log) {
        this.detection = detection;
        this.rights = rights;
        this.tr = tr;
        this.host = host;
        this.log = log;
    }

    void start(Target target, BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        KeyDetection.Mode mode = detection.mode();
        Function<PanelRights, Object> relevant =
                target == Target.OWN_KEY ? PanelRights::panelEnabled : current -> current;
        Object atStart = relevant.apply(rights.get());
        long startedAt = System.nanoTime();
        detection.detect(
                mode,
                result -> HytalePages.onWorldThread(host.viewer(), () -> {
                    boolean rightsUnchanged = relevant.apply(rights.get()).equals(atStart);
                    boolean timedOut = System.nanoTime() - startedAt
                            >= TimeUnit.SECONDS.toNanos(
                                    mode == KeyDetection.Mode.GAME
                                            ? detection.gameTimeoutSeconds()
                                            : detection.screenTimeoutSeconds());
                    finish(mode, result, target == Target.BIND_FORM, rightsUnchanged, timedOut, applyKeys);
                }));
        if (mode == KeyDetection.Mode.GAME) {
            host.closePanel();
        }
    }

    private void finish(
            KeyDetection.Mode mode,
            Optional<String> result,
            boolean reopenWithoutKey,
            boolean rightsUnchanged,
            boolean timedOut,
            BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        try {
            if (result.isPresent() && !rightsUnchanged) {
                HytalePages.tell(host.viewer(), tr.t("controlcore.keys.detect.rights_changed"));
                return;
            }
            if (result.isEmpty() && mode == KeyDetection.Mode.GAME && !reopenWithoutKey) {
                HytalePages.tell(
                        host.viewer(),
                        tr.t("controlcore.keys.detect.timed_out", "seconds", detection.gameTimeoutSeconds()));
                return;
            }
            HytalePages.Reopen reopened =
                    HytalePages.reopenIfFree(host.viewer(), host.page(), HytaleKeyCapture::isCapturePage);
            if (reopened != HytalePages.Reopen.REOPENED && reopened != HytalePages.Reopen.ALREADY_OPEN) {
                result.ifPresent(keys -> HytalePages.tell(host.viewer(), notSaved(reopened, keys)));
                return;
            }
            String text = result.map(keys -> host.asViewer(() -> applyKeys.apply(keys, mode)))
                    .orElseGet(() -> cancelled(mode, timedOut));
            if (text != null) {
                UICommandBuilder update = new UICommandBuilder();
                update.set(FOOTER, text);
                host.send(update);
            }
        } catch (RuntimeException e) {
            log.error("Detect key failed for " + host.viewer().getUsername(), e);
        }
    }

    private String cancelled(KeyDetection.Mode mode, boolean timedOut) {
        if (mode == KeyDetection.Mode.GAME) {
            return tr.t("controlcore.keys.detect.timed_out_here", "seconds", detection.gameTimeoutSeconds());
        }
        return timedOut
                ? tr.t("controlcore.keys.detect.screen_timed_out", "minutes", detection.screenTimeoutSeconds() / 60)
                : tr.t("controlcore.keys.detect.cancelled");
    }

    private String notSaved(HytalePages.Reopen reason, String keys) {
        String shown = KeyPicker.describe(tr, keys);
        return switch (reason) {
            case DEAD -> tr.t("controlcore.keys.detect.not_saved_dead", "keys", shown);
            case BUSY -> tr.t("controlcore.keys.detect.not_saved", "keys", shown);
            default -> tr.t("controlcore.keys.detect.not_saved_gone", "keys", shown);
        };
    }
}
