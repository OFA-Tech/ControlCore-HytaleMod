package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.Messenger;

public final class FakeMessenger implements Messenger {

    public record Sent(PlayerId to, String text) {}

    public final List<Sent> sent = new CopyOnWriteArrayList<>();
    public final List<String> broadcasts = new CopyOnWriteArrayList<>();

    @Override
    public void send(PlayerId player, String text) {
        sent.add(new Sent(player, text));
    }

    @Override
    public void broadcast(String text) {
        broadcasts.add(text);
    }

    public List<String> textsTo(PlayerId player) {
        return sent.stream().filter(s -> s.to().equals(player)).map(Sent::text).toList();
    }
}
