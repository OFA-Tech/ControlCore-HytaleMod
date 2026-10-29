package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.model.PanelRights;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.infrastructure.platforms.hytale.input.HytaleKeyCapture;
import net.ofatech.controlcore.infrastructure.platforms.hytale.pages.HytalePages;

final class PanelKeyDetect {

    private static final String FOOTER = "#FooterStatus.Text";

    interface Host {
        PlayerRef viewer();

        CustomUIPage page();

        void closePanel();

        boolean forgetUnsavedRows();

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

    void start(boolean reopenWithoutKey, BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        KeyDetection.Mode mode = detection.mode();
        PanelRights atStart = rights.get();
        BooleanSupplier rightsUnchanged = () -> rights.get().equals(atStart);
        detection.detect(
                mode,
                result -> HytalePages.onWorldThread(
                        host.viewer(), () -> finish(mode, result, reopenWithoutKey, rightsUnchanged, applyKeys)));
        if (mode == KeyDetection.Mode.GAME) {
            host.closePanel();
        }
    }

    private void finish(
            KeyDetection.Mode mode,
            Optional<String> result,
            boolean reopenWithoutKey,
            BooleanSupplier rightsUnchanged,
            BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        try {
            if (!rightsUnchanged.getAsBoolean()) {
                HytalePages.tell(host.viewer(), tr.t("controlcore.keys.detect.rights_changed"));
                return;
            }
            if (result.isEmpty() && mode == KeyDetection.Mode.GAME && !reopenWithoutKey) {
                HytalePages.tell(
                        host.viewer(),
                        tr.t("controlcore.keys.detect.timed_out", "seconds", detection.gameTimeoutSeconds()));
                return;
            }
            boolean[] dropped = new boolean[1];
            HytalePages.Reopen reopened = HytalePages.reopenIfFree(
                    host.viewer(),
                    host.page(),
                    HytaleKeyCapture::isCapturePage,
                    () -> dropped[0] = host.forgetUnsavedRows());
            if (reopened != HytalePages.Reopen.REOPENED && reopened != HytalePages.Reopen.ALREADY_OPEN) {
                result.ifPresent(keys -> HytalePages.tell(host.viewer(), notSaved(reopened, keys)));
                return;
            }
            String text = result.map(keys -> host.asViewer(() -> applyKeys.apply(keys, mode)))
                    .orElseGet(() -> mode == KeyDetection.Mode.GAME
                            ? tr.t("controlcore.keys.detect.timed_out", "seconds", detection.gameTimeoutSeconds())
                            : tr.t("controlcore.keys.detect.cancelled"));
            if (text != null) {
                if (dropped[0]) {
                    text = text + " " + tr.t("controlcore.keys.detect.unsaved_dropped");
                }
                UICommandBuilder update = new UICommandBuilder();
                update.set(FOOTER, text);
                host.send(update);
            }
        } catch (RuntimeException e) {
            log.error("Detect key failed for " + host.viewer().getUsername(), e);
        }
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
