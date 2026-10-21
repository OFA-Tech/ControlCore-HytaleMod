package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface Messenger {

    void send(PlayerId player, String text);

    /** Sends {@code text} in a colour ({@code #rrggbb}). */
    default void send(PlayerId player, String text, String color) {
        send(player, text);
    }

    void broadcast(String text);
}
