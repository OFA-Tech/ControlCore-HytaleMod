package net.ofatech.controlcore.application.serverconfig;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class OutsideChangesTask implements ModTask {

    private final ServerConfigEditor serverConfig;

    public OutsideChangesTask(ServerConfigEditor serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(Duration.ofSeconds(5));
    }

    @Override
    public void run() {
        serverConfig.checkOutsideChanges();
    }
}
