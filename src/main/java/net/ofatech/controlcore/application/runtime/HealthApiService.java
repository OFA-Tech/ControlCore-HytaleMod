package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.List;
import net.ofatech.controlcore.api.HealthApi;
import net.ofatech.controlcore.application.diagnostics.DiagnosticsService;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(HealthApi.class)
public final class HealthApiService implements HealthApi {

    private final DiagnosticsService diagnostics;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public HealthApiService(DiagnosticsService diagnostics, Texts texts, PlayerDirectory directory) {
        this(diagnostics, texts, directory, ApiActor.MOD);
    }

    private HealthApiService(DiagnosticsService diagnostics, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.diagnostics = diagnostics;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public HealthApi actingFor(PlayerId player) {
        return new HealthApiService(diagnostics, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Section> sections() {
        if (!diagnostics.maySee(actor.who())) {
            return List.of();
        }
        return diagnostics.sections(texts.forPlayer(actor.who())).stream()
                .map(section -> new Section(
                        section.title(),
                        section.lines().stream()
                                .map(line -> new Line(line.label(), line.value(), line.warning()))
                                .toList()))
                .toList();
    }

    @Override
    public Result<String> writeReport() {
        return diagnostics.writeReportFile(actor.who());
    }
}
