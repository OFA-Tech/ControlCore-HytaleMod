package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.infrastructure.platforms.hytale.pages.HytalePages;

final class PanelKeyDetect {

    private static final String FOOTER = "#FooterStatus.Text";

    interface Host {
        PlayerRef viewer();

        CustomUIPage page();

        void closePanel();

        void beforeReopen();

        void send(UICommandBuilder update);

        String asViewer(Supplier<String> work);
    }

    private final KeyDetection detection;
    private final Translator tr;
    private final Host host;
    private final Log log;

    PanelKeyDetect(KeyDetection detection, Translator tr, Host host, Log log) {
        this.detection = detection;
        this.tr = tr;
        this.host = host;
        this.log = log;
    }

    void start(BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        KeyDetection.Mode[] used = new KeyDetection.Mode[1];
        used[0] = detection.detect(
                result -> HytalePages.onWorldThread(host.viewer(), () -> finish(used[0], result, applyKeys)));
        if (used[0] == KeyDetection.Mode.GAME) {
            host.closePanel();
        }
    }

    private void finish(
            KeyDetection.Mode mode, Optional<String> result, BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        try {
            if (!detection.rightsUnchanged()) {
                HytalePages.tell(host.viewer(), tr.t("controlcore.keys.detect.rights_changed"));
                return;
            }
            if (result.isEmpty() && mode == KeyDetection.Mode.GAME) {
                HytalePages.tell(
                        host.viewer(), tr.t("controlcore.keys.detect.none", "seconds", detection.timeoutSeconds()));
                return;
            }
            host.beforeReopen();
            HytalePages.Reopen reopened = HytalePages.reopenIfFree(host.viewer(), host.page());
            if (reopened != HytalePages.Reopen.DONE) {
                result.ifPresent(keys -> HytalePages.tell(host.viewer(), notSaved(reopened, keys)));
                return;
            }
            String text = result.map(keys -> host.asViewer(() -> applyKeys.apply(keys, mode)))
                    .orElseGet(() -> tr.t("controlcore.keys.detect.cancelled"));
            if (text != null) {
                UICommandBuilder update = new UICommandBuilder();
                update.set(FOOTER, text);
                host.send(update);
            }
        } catch (RuntimeException e) {
            log.error("Detect key failed for " + host.viewer().getUsername(), e);
        }
    }

    private String notSaved(HytalePages.Reopen reason, String keys) {
        return switch (reason) {
            case DEAD -> tr.t("controlcore.keys.detect.not_saved_dead", "keys", keys);
            case BUSY -> tr.t("controlcore.keys.detect.not_saved", "keys", keys);
            default -> tr.t("controlcore.keys.detect.not_saved_gone", "keys", keys);
        };
    }
}
