package net.ofatech.controlcore.application.worlds;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;

@AutoService(ModTask.class)
public final class WorldsTask implements ModTask {

    private final WorldsService worlds;

    public WorldsTask(WorldsService worlds) {
        this.worlds = worlds;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(Duration.ofSeconds(10));
    }

    @Override
    public void run() {
        worlds.tick();
    }
}
