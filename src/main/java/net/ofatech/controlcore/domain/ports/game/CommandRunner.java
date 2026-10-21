package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface CommandRunner {

    void runAs(PlayerId player, String command);

    void runAsServer(String command);

    void runInBackground(String description, Runnable work);
}
