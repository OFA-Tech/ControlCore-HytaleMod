package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;

final class PanelKeyDetect {

    private static final String FOOTER = "#FooterStatus.Text";

    interface Host {
        PlayerRef viewer();

        CustomUIPage page();

        void closePanel();

        void send(UICommandBuilder update);

        String asViewer(Supplier<String> work);
    }

    private final KeyDetection detection;
    private final Translator tr;
    private final Host host;

    PanelKeyDetect(KeyDetection detection, Translator tr, Host host) {
        this.detection = detection;
        this.tr = tr;
        this.host = host;
    }

    void start(BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        AtomicReference<KeyDetection.Mode> used = new AtomicReference<>();
        Optional<KeyDetection.Mode> mode =
                detection.detect(result -> onWorldThread(() -> finish(used.get(), result, applyKeys)));
        if (mode.isEmpty()) {
            UICommandBuilder update = new UICommandBuilder();
            update.set(FOOTER, tr.t("controlcore.keys.detect.busy"));
            host.send(update);
            return;
        }
        used.set(mode.get());
        if (mode.get() == KeyDetection.Mode.GAME) {
            host.closePanel();
        }
    }

    private void finish(
            KeyDetection.Mode mode, Optional<String> result, BiFunction<String, KeyDetection.Mode, String> applyKeys) {
        if (!detection.stillAllowed()) {
            detection.tell(tr.t("controlcore.keys.detect.toast_title"), tr.t("controlcore.keys.detect.rights_changed"));
            return;
        }
        if (!reopen()) {
            result.ifPresent(keys -> detection.tell(
                    tr.t("controlcore.keys.detect.toast_title"),
                    tr.t("controlcore.keys.detect.not_saved", "keys", keys)));
            return;
        }
        String text = result.map(keys -> host.asViewer(() -> applyKeys.apply(keys, mode)))
                .orElseGet(() -> mode == KeyDetection.Mode.SCREEN
                        ? tr.t("controlcore.keys.detect.cancelled")
                        : tr.t("controlcore.keys.detect.none", "seconds", 10));
        if (text != null) {
            UICommandBuilder update = new UICommandBuilder();
            update.set(FOOTER, text);
            host.send(update);
        }
    }

    private boolean reopen() {
        Ref<EntityStore> ref = host.viewer().getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            return false;
        }
        CustomUIPage current = player.getPageManager().getCustomPage();
        boolean otherScreen = (current != null && current != host.page())
                || player.getPageManager().getActiveServersidePage() != null
                || !player.getWindowManager().getWindows().isEmpty();
        if (otherScreen) {
            return false;
        }
        if (current != host.page()) {
            player.getPageManager().openCustomPage(ref, store, host.page());
        }
        return true;
    }

    private void onWorldThread(Runnable work) {
        Ref<EntityStore> ref = host.viewer().getReference();
        if (ref != null && ref.isValid()) {
            ref.getStore().getExternalData().getWorld().execute(work);
        }
    }
}
