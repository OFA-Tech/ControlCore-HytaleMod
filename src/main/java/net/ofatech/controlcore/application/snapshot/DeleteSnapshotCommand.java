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
public final class DeleteSnapshotCommand implements ModCommand {

    private final SnapshotService snapshots;
    private final Texts texts;

    public DeleteSnapshotCommand(SnapshotService snapshots, Texts texts) {
        this.snapshots = snapshots;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-snapshot-delete")
                .description("Delete a saved snapshot")
                .arg("name", ArgType.STRING, "Snapshot name");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return snapshots
                .delete(context.string("name"), SnapshotCommands.who(context))
                .map(name ->
                        Reply.of(texts.forSender(context.sender()).t("controlcore.snapshots.deleted", "name", name)));
    }
}
