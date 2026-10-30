package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyCapture;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.infrastructure.platforms.hytale.pages.HytalePages;

public final class HytaleKeyCapture implements KeyCapture {

    private final Log log;

    public HytaleKeyCapture(Log log) {
        this.log = log;
    }

    public static boolean isCapturePage(CustomUIPage page) {
        return page instanceof KeyCapturePage;
    }

    @Override
    public void close(PlayerId player) {
        HytalePages.inWorld(player.value())
                .ifPresent(playerRef -> HytalePages.onWorldThread(
                        playerRef, () -> HytalePages.closeIf(playerRef, HytaleKeyCapture::isCapturePage)));
    }

    @Override
    public void open(PlayerId player, Translator tr, BooleanSupplier anyKey, Consumer<Optional<String>> done) {
        boolean opening = HytalePages.inWorld(player.value())
                .map(playerRef -> HytalePages.open(
                        playerRef,
                        ref -> new KeyCapturePage(ref, tr, anyKey, done),
                        log,
                        "the key capture screen",
                        () -> done.accept(Optional.empty())))
                .orElse(false);
        if (!opening) {
            done.accept(Optional.empty());
        }
    }
}
