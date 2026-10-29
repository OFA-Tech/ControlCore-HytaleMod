package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class SnapshotDiffCommand implements ModCommand {

    private final SnapshotService snapshots;
    private final Texts texts;

    public SnapshotDiffCommand(SnapshotService snapshots, Texts texts) {
        this.snapshots = snapshots;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-snapshot-diff")
                .description("Show what restoring a snapshot would change, without changing anything")
                .arg("name", ArgType.STRING, "Snapshot name");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        return snapshots
                .plan(context.string("name"), SnapshotCommands.who(context), tr)
                .map(plan -> Reply.of(text(snapshots.preview(plan, tr), tr)));
    }

    static String text(SnapshotsView.Preview preview, Translator tr) {
        StringBuilder text = new StringBuilder(preview.title(tr));
        preview.allLines(tr).forEach(line -> text.append('\n').append(line));
        return text.toString();
    }
}
