package net.ofatech.controlcore.domain.ports.game;

import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.text.Translator;

public interface KeyCapture {

    void open(PlayerId player, Translator tr, boolean anyKey, Consumer<Optional<String>> done);

    void close(PlayerId player);
}
