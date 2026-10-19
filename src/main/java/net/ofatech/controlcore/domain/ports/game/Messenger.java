package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface Messenger {

    void send(PlayerId player, String text);

    void broadcast(String text);
}
