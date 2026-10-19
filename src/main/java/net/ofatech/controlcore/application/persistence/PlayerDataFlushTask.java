package net.ofatech.controlcore.application.persistence;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.TaskSchedule;
import net.ofatech.controlcore.domain.ports.game.PlayerData;

@AutoService(ModTask.class)
public final class PlayerDataFlushTask implements ModTask {

    private final PlayerData playerData;

    public PlayerDataFlushTask(PlayerData playerData) {
        this.playerData = playerData;
    }

    @Override
    public TaskSchedule schedule() {
        return TaskSchedule.every(Duration.ofMinutes(5));
    }

    @Override
    public void run() {
        playerData.flush();
    }

    @Override
    public boolean runOnShutdown() {
        return true;
    }
}
