package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;
import java.util.function.Consumer;

public interface KeyDetection {

    KeyDetection NONE = new KeyDetection() {
        @Override
        public boolean allKeys() {
            return false;
        }

        @Override
        public boolean fromScreen() {
            return false;
        }

        @Override
        public void detect(Consumer<Optional<String>> done) {
            done.accept(Optional.empty());
        }
    };

    boolean allKeys();

    boolean fromScreen();

    void detect(Consumer<Optional<String>> done);
}
