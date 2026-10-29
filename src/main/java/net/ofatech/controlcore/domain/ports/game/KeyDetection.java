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
        public Optional<Mode> detect(Consumer<Optional<String>> done) {
            return Optional.empty();
        }

        @Override
        public boolean stillAllowed() {
            return true;
        }

        @Override
        public void tell(String title, String text) {}
    };

    boolean allKeys();

    Mode mode();

    Optional<Mode> detect(Consumer<Optional<String>> done);

    boolean stillAllowed();

    void tell(String title, String text);
}
