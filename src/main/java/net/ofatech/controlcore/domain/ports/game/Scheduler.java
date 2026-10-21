package net.ofatech.controlcore.domain.ports.game;

import java.time.Duration;

public interface Scheduler {

    ScheduledTask schedule(Duration initialDelay, Duration period, Runnable work);

    ScheduledTask runLater(Duration delay, Runnable work);

    @FunctionalInterface
    interface ScheduledTask {
        void cancel();
    }
}
