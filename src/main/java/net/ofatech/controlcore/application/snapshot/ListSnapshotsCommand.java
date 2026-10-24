package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ListSnapshotsCommand implements ModCommand {

    private final SnapshotService snapshots;
    private final Texts texts;

    public ListSnapshotsCommand(SnapshotService snapshots, Texts texts) {
        this.snapshots = snapshots;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-snapshots").description("List the saved snapshots");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        if (!snapshots.mayUse(SnapshotCommands.who(context))) {
            return Result.failure(Error.forbidden(
                    "Snapshots.Forbidden",
                    Text.key("controlcore.errors.snapshots.forbidden", "Only admins can use snapshots.")));
        }
        Translator tr = texts.forSender(context.sender());
        List<Snapshot> all = snapshots.all();
        if (all.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.snapshots.none")));
        }
        StringBuilder text = new StringBuilder(tr.t("controlcore.snapshots.list.header", "count", all.size()));
        for (Snapshot snapshot : all) {
            text.append('\n')
                    .append(tr.t(
                            "controlcore.snapshots.list.line",
                            "name",
                            snapshot.name(),
                            "when",
                            snapshots.when(snapshot),
                            "who",
                            snapshot.createdBy(),
                            "parts",
                            snapshots.partsText(snapshot, tr)));
        }
        return Result.success(Reply.of(text.toString()));
    }
}
