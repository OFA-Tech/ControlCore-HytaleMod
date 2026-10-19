package net.ofatech.controlcore.domain.ports.game;

import java.time.Duration;

/** Runs work later or repeatedly. Work runs off the world thread. */
public interface Scheduler {

    ScheduledTask schedule(Duration initialDelay, Duration period, Runnable work);

    ScheduledTask runLater(Duration delay, Runnable work);

    @FunctionalInterface
    interface ScheduledTask {
        void cancel();
    }
}
