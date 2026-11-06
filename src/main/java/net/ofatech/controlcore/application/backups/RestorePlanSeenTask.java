package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class RestorePlanSeenTask implements ModTask {

    static final Duration EVERY = Duration.ofHours(1);

    private final BackupService backups;

    public RestorePlanSeenTask(BackupService backups) {
        this.backups = backups;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(EVERY);
    }

    @Override
    public void run() {
        backups.stillPlanned();
    }
}
