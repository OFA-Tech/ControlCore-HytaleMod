package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface Messenger {

    void send(PlayerId player, String text);

    default void send(PlayerId player, String text, String color) {
        send(player, text);
    }

    void broadcast(String text);

    default void title(PlayerId player, String title, String subtitle) {
        send(player, subtitle.isBlank() ? title : title + " - " + subtitle);
    }

    default void titleAll(String title, String subtitle) {
        broadcast(subtitle.isBlank() ? title : title + " - " + subtitle);
    }
}
