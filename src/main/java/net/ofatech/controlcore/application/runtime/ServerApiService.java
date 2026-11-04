package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.util.Optional;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.application.maintenance.MaintenanceService;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.restart.RestartService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ServerApi.class)
public final class ServerApiService implements ServerApi {

    private final MaintenanceService maintenance;
    private final RestartService restart;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public ServerApiService(
            MaintenanceService maintenance, RestartService restart, Texts texts, PlayerDirectory directory) {
        this(maintenance, restart, texts, directory, ApiActor.MOD);
    }

    private ServerApiService(
            MaintenanceService maintenance,
            RestartService restart,
            Texts texts,
            PlayerDirectory directory,
            ApiActor actor) {
        this.maintenance = maintenance;
        this.restart = restart;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public ServerApi actingFor(PlayerId player) {
        return new ServerApiService(maintenance, restart, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public Maintenance maintenance() {
        MaintenanceState state = maintenance.state();
        return new Maintenance(state.on(), state.message(), state.motd(), state.since(), state.until());
    }

    @Override
    public Result<String> setMaintenance(boolean on, MaintenanceOptions options) {
        MaintenanceOptions chosen = options == null ? MaintenanceOptions.DEFAULT : options;
        Optional<Duration> offAfter = chosen.offAfter() == null ? Optional.empty() : chosen.offAfter();
        return actor.run(() -> on
                ? maintenance.turnOn(chosen.message(), chosen.motd(), offAfter, actor.who(), tr())
                : maintenance.turnOff(actor.who(), tr()));
    }

    @Override
    public Result<String> restart(Duration delay, String reason) {
        return actor.run(() -> restart.start(Optional.ofNullable(delay), reason, actor.who(), tr()));
    }

    @Override
    public Result<String> cancelRestart() {
        return actor.run(() -> restart.cancel(actor.who(), tr()));
    }

    @Override
    public RestartStatus restartStatus() {
        return new RestartStatus(restart.counting(), restart.remaining(), restart.reason());
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }
}
