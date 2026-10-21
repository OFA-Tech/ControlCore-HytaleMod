package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface Messenger {

    void send(PlayerId player, String text);

    default void send(PlayerId player, String text, String color) {
        send(player, text);
    }

    void broadcast(String text);
}
