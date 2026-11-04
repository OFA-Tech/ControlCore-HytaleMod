package net.ofatech.controlcore.application.maintenance;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class MaintenanceTask implements ModTask {

    static final Duration EVERY = Duration.ofSeconds(15);

    private final MaintenanceService maintenance;

    public MaintenanceTask(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(EVERY);
    }

    @Override
    public void run() {
        maintenance.tick();
    }
}
