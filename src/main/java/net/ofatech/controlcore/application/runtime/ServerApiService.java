package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.application.maintenance.MaintenanceService;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ServerApi.class)
public final class ServerApiService implements ServerApi {

    private final MaintenanceService maintenance;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public ServerApiService(MaintenanceService maintenance, Texts texts, PlayerDirectory directory) {
        this(maintenance, texts, directory, ApiActor.MOD);
    }

    private ServerApiService(MaintenanceService maintenance, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.maintenance = maintenance;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public ServerApi actingFor(PlayerId player) {
        return new ServerApiService(maintenance, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public Maintenance maintenance() {
        MaintenanceState state = maintenance.state();
        return new Maintenance(state.on(), state.message(), state.motd(), state.since(), state.until());
    }

    @Override
    public Result<String> setMaintenance(boolean on, MaintenanceOptions options) {
        MaintenanceOptions chosen = options == null ? MaintenanceOptions.DEFAULT : options;
        Optional<java.time.Duration> offAfter = chosen.offAfter() == null ? Optional.empty() : chosen.offAfter();
        return actor.run(() -> on
                ? maintenance.turnOn(chosen.message(), chosen.motd(), offAfter, actor.who(), tr())
                : maintenance.turnOff(actor.who(), tr()));
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }
}
