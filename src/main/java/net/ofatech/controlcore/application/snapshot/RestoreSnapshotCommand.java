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

    public RestoreSnapshotCommand(SnapshotService snapshots, Texts texts) {
        this.snapshots = snapshots;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-snapshot-restore")
                .description("Restore a snapshot (check it first with /cc-snapshot-diff)")
                .arg("name", ArgType.STRING, "Snapshot name")
                .optionalArg("safety", ArgType.STRING, "save-first: save the current state as before-<name> first");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        boolean saveFirst = context.arg("safety")
                .map(Object::toString)
                .map(text -> text.strip().equalsIgnoreCase("save-first"))
                .orElse(false);
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
