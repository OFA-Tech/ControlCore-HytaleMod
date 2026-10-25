package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class AutomationTask implements ModTask {

    static final Duration EVERY = Duration.ofSeconds(5);

    private final AutomationService automation;

    public AutomationTask(AutomationService automation) {
        this.automation = automation;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(EVERY);
    }

    @Override
    public void run() {
        automation.tick();
    }
}
