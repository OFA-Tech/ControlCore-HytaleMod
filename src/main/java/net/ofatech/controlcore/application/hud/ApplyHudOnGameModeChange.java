package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import java.time.Duration;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerGameModeChanged;
import net.ofatech.controlcore.domain.ports.game.Scheduler;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ApplyHudOnGameModeChange implements ModEventHandler<PlayerGameModeChanged> {

    static final Duration AFTER_THE_CLIENT_RESETS_ITS_HUD = Duration.ofMillis(500);

    private final HudService hud;
    private final Scheduler scheduler;

    public ApplyHudOnGameModeChange(HudService hud, Scheduler scheduler) {
        this.hud = hud;
        this.scheduler = scheduler;
    }

    @Override
    public Class<PlayerGameModeChanged> eventType() {
        return PlayerGameModeChanged.class;
    }

    @Override
    public void handle(PlayerGameModeChanged event) {
        scheduler.runLater(
                AFTER_THE_CLIENT_RESETS_ITS_HUD, () -> hud.apply(event.player().id()));
    }
}
