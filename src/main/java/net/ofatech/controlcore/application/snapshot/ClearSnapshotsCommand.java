package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ClearSnapshotsCommand implements ModCommand {

    private final SnapshotService snapshots;
    private final Texts texts;

    public ClearSnapshotsCommand(SnapshotService snapshots, Texts texts) {
        this.snapshots = snapshots;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-snapshots-clear")
                .description("Delete every snapshot that is not marked as a profile");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return snapshots
                .deleteAll(SnapshotCommands.who(context))
                .map(count -> Reply.of(snapshots.deletedText(
                        count, SnapshotCommands.who(context), texts.forSender(context.sender()))));
    }
}
