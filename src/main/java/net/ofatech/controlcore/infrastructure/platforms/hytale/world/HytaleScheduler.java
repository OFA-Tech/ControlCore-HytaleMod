package net.ofatech.controlcore.infrastructure.platforms.hytale.world;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.task.TaskRegistry;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleScheduler implements Scheduler {

    private final TaskRegistry tasks;
    private final Log log;

    public HytaleScheduler(TaskRegistry tasks, Log log) {
        this.tasks = tasks;
        this.log = log;
    }

    @Override
    public ScheduledTask schedule(Duration initialDelay, Duration period, Runnable work) {
        return track(HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                safe(work), initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public ScheduledTask runLater(Duration delay, Runnable work) {
        return track(HytaleServer.SCHEDULED_EXECUTOR.schedule(safe(work), delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @SuppressWarnings("unchecked")
    private ScheduledTask track(ScheduledFuture<?> future) {
        tasks.registerTask((ScheduledFuture<Void>) future);
        return () -> future.cancel(false);
    }

    private Runnable safe(Runnable work) {
        return () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                log.error("A scheduled task failed", e);
            }
        };
    }
}
