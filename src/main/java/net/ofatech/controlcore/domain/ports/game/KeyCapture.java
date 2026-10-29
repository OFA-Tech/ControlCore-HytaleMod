package net.ofatech.controlcore.domain.ports.game;

import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface KeyCapture {

    void open(PlayerId player, Consumer<Optional<String>> done);
}
