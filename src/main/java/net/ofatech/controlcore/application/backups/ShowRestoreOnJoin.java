package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ShowRestoreOnJoin implements ModEventHandler<PlayerJoined> {

    private final BackupService backups;

    public ShowRestoreOnJoin(BackupService backups) {
        this.backups = backups;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        backups.showStartupTo(event.player().id());
    }
}
