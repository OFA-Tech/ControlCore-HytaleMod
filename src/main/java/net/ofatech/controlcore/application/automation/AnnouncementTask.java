package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class AnnouncementTask implements ModTask {

    static final Duration EVERY = Duration.ofSeconds(10);

    private final AnnouncementService announcements;

    public AnnouncementTask(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(EVERY);
    }

    @Override
    public void run() {
        announcements.tick();
    }
}
