package net.ofatech.controlcore.application.component;

import java.time.Duration;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

public record TaskSchedule(Duration initialDelay, Optional<Duration> period) {

    public TaskSchedule {
        Guard.notNull(initialDelay, "initialDelay");
        Guard.notNull(period, "period");
    }

    public static TaskSchedule every(Duration period) {
        return new TaskSchedule(period, Optional.of(period));
    }

    public static TaskSchedule after(Duration delay) {
        return new TaskSchedule(delay, Optional.empty());
    }
}
