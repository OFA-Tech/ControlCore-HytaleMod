package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RestoreSnapshotCommand implements ModCommand {

    private final SnapshotService snapshots;
    private final Texts texts;
    private final boolean saveFirst;

    public RestoreSnapshotCommand(SnapshotService snapshots, Texts texts) {
        this(snapshots, texts, false);
    }

    RestoreSnapshotCommand(SnapshotService snapshots, Texts texts, boolean saveFirst) {
        this.snapshots = snapshots;
        this.texts = texts;
        this.saveFirst = saveFirst;
    }

    @Override
    public CommandSpec spec() {
        if (saveFirst) {
            return CommandSpec.named("cc-snapshot-restore-safe")
                    .description("Save the current state as before-<name>, then restore the snapshot")
                    .arg("name", ArgType.STRING, "Snapshot name");
        }
        return CommandSpec.named("cc-snapshot-restore")
                .description("Restore a snapshot (check it first with /cc-snapshot-diff)")
                .arg("name", ArgType.STRING, "Snapshot name");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return snapshots
                .restore(
                        context.string("name"),
                        saveFirst,
                        SnapshotCommands.who(context),
                        SnapshotCommands.actor(context),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
