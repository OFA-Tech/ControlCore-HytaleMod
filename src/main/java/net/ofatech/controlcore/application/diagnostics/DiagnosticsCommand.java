package net.ofatech.controlcore.application.diagnostics;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.diagnostics.DiagnosticsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class DiagnosticsCommand implements ModCommand {

    private final DiagnosticsService diagnostics;
    private final Texts texts;

    public DiagnosticsCommand(DiagnosticsService diagnostics, Texts texts) {
        this.diagnostics = diagnostics;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-diagnostics")
                .description("Show the server's health and write a shareable report (passwords and IPs removed)");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        return diagnostics.writeReport(who, tr).map(written -> {
            List<String> lines = new ArrayList<>();
            for (DiagnosticsView.Section section : diagnostics.sections(tr)) {
                lines.add("[" + section.title() + "]");
                for (DiagnosticsView.Line line : section.lines()) {
                    lines.add((line.warning() ? "! " : "  ") + line.label() + ": " + line.value());
                }
            }
            lines.add(written);
            return Reply.of(String.join("\n", lines));
        });
    }
}
