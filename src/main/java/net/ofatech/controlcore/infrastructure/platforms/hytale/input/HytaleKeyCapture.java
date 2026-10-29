package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import java.util.Optional;
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

    @Override
    public void open(PlayerId player, Translator tr, Consumer<Optional<String>> doneOnTheWorldThread) {
        boolean opening = HytalePages.inWorld(player.value())
                .map(playerRef -> HytalePages.open(
                        playerRef,
                        ref -> new KeyCapturePage(ref, tr, doneOnTheWorldThread),
                        log,
                        "the key capture screen",
                        () -> doneOnTheWorldThread.accept(Optional.empty())))
                .orElse(false);
        if (!opening) {
            doneOnTheWorldThread.accept(Optional.empty());
        }
    }
}
