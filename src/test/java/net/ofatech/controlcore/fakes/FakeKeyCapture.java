package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.KeyCapture;

public final class FakeKeyCapture implements KeyCapture {

    public final List<PlayerId> opened = new CopyOnWriteArrayList<>();
    private final Map<PlayerId, Consumer<Optional<String>>> waiting = new ConcurrentHashMap<>();

    @Override
    public void open(PlayerId player, Consumer<Optional<String>> done) {
        opened.add(player);
        waiting.put(player, done);
    }

    public void press(PlayerId player, String keys) {
        Consumer<Optional<String>> done = waiting.remove(player);
        if (done != null) {
            done.accept(Optional.ofNullable(keys));
        }
    }
}
