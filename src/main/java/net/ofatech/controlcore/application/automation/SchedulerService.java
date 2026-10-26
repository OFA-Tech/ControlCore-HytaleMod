package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import net.ofatech.controlcore.api.SchedulerApi;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.system.Clock;

@AutoService(SchedulerApi.class)
public final class SchedulerService implements SchedulerApi {

    private final Scheduler scheduler;
    private final Clock clock;

    public SchedulerService(Scheduler scheduler, Clock clock) {
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Override
    public Task later(Duration delay, Runnable work) {
        Objects.requireNonNull(work, "work");
        Scheduler.ScheduledTask task = scheduler.runLater(delay.isNegative() ? Duration.ZERO : delay, work);
        return task::cancel;
    }

    @Override
    public Task every(Duration period, Runnable work) {
        Objects.requireNonNull(work, "work");
        if (period == null || period.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("The period must be at least one second");
        }
        Scheduler.ScheduledTask task = scheduler.schedule(period, period, work);
        return task::cancel;
    }

    @Override
    public Task dailyAt(LocalTime time, Runnable work) {
        Objects.requireNonNull(work, "work");
        Scheduler.ScheduledTask task = scheduler.schedule(untilNext(time), Duration.ofDays(1), work);
        return task::cancel;
    }

    Duration untilNext(LocalTime time) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.now(), ZoneId.systemDefault());
        LocalDateTime next = now.toLocalDate().atTime(time);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next);
    }
}
