package net.ofatech.controlcore.application.component;

public interface ModTask {

    TaskSchedule schedule();

    void run();

    default boolean runOnShutdown() {
        return false;
    }
}
