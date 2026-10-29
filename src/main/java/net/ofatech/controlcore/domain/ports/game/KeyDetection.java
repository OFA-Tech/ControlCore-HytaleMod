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
            return Mode.GAME;
        }

        @Override
        public long timeoutSeconds() {
            return 0;
        }

        @Override
        public Mode detect(Consumer<Optional<String>> done) {
            done.accept(Optional.empty());
            return Mode.GAME;
        }

        @Override
        public boolean rightsUnchanged() {
            return true;
        }
    };

    boolean allKeys();

    Mode mode();

    long timeoutSeconds();

    Mode detect(Consumer<Optional<String>> done);

    boolean rightsUnchanged();
}
