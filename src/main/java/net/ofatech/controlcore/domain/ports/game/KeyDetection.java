package net.ofatech.controlcore.domain.ports.game;

import java.util.Optional;
import java.util.function.Consumer;

public interface KeyDetection {

    enum Mode {
        GAME,
        SCREEN
    }

    KeyDetection NONE = new KeyDetection() {
        @Override
        public boolean allKeys() {
            return false;
        }

        @Override
        public Mode mode() {
            return Mode.SCREEN;
        }

        @Override
        public long gameTimeoutSeconds() {
            return 0;
        }

        @Override
        public void detect(Mode mode, Consumer<Optional<String>> done) {
            done.accept(Optional.empty());
        }
    };

    boolean allKeys();

    Mode mode();

    long gameTimeoutSeconds();

    void detect(Mode mode, Consumer<Optional<String>> done);
}
