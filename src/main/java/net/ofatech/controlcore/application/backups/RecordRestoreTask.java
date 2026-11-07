package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class RecordRestoreTask implements ModTask {

    private final BackupService backups;

    public RecordRestoreTask(BackupService backups) {
        this.backups = backups;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.after(Duration.ofSeconds(1));
    }

    @Override
    public void run() {
        backups.recordStartup();
    }
}
