package net.ofatech.controlcore.application.serverconfig;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class OutsideChangesTask implements ModTask {

    private final ServerConfigEditor serverConfig;
    private final BansService bans;

    public OutsideChangesTask(ServerConfigEditor serverConfig, BansService bans) {
        this.serverConfig = serverConfig;
        this.bans = bans;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(Duration.ofSeconds(5));
    }

    @Override
    public void run() {
        serverConfig.checkOutsideChanges();
        bans.checkOutsideChanges();
    }
}
