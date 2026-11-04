package net.ofatech.controlcore.domain.ports.game;

public interface ServerControl {

    void stop(String message);

    void onStopping(Runnable listener);
}
