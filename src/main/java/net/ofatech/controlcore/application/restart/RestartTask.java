package net.ofatech.controlcore.application.restart;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class RestartTask implements ModTask {

    static final Duration EVERY = Duration.ofSeconds(1);

    private final RestartService restart;

    public RestartTask(RestartService restart) {
        this.restart = restart;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(EVERY);
    }

    @Override
    public void run() {
        restart.tick();
    }
}
