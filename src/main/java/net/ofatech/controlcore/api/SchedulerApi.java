package net.ofatech.controlcore.api;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Runs code later, repeatedly, or every day at a time of the server's clock. The work runs on a background thread:
 * hop to a world's thread before touching that world. A failing run is logged and does not stop later runs.
 *
 * <pre>{@code
 * Task task = api.scheduler().every(Duration.ofMinutes(30), () -> api.notifications().broadcast("Vote!"));
 * task.cancel();
 * }</pre>
 *
 * @since API 2.1.0
 */
public interface SchedulerApi {

    /** A scheduled piece of work that can be stopped. */
    @FunctionalInterface
    interface Task {

        /** Stops the work; a run in progress finishes. Calling it again does nothing. */
        void cancel();
    }

    /**
     * Runs work once after a delay.
     *
     * @param delay how long to wait
     * @param work the work to run
     * @return a handle to cancel it
     */
    Task later(Duration delay, Runnable work);

    /**
     * Runs work repeatedly; the first run is one period from now.
     *
     * @param period the time between runs, at least one second
     * @param work the work to run
     * @return a handle to stop it
     * @throws IllegalArgumentException if the period is shorter than one second
     */
    Task every(Duration period, Runnable work);

    /**
     * Runs work every day at a time of the server's clock.
     *
     * @param time the time of day, for example {@code LocalTime.of(20, 0)}
     * @param work the work to run
     * @return a handle to stop it
     */
    Task dailyAt(LocalTime time, Runnable work);
}
