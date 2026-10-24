package net.ofatech.controlcore.application.snapshot;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RestoreSnapshotSafeCommand implements ModCommand {

    private final RestoreSnapshotCommand restore;

    public RestoreSnapshotSafeCommand(SnapshotService snapshots, Texts texts) {
        this.restore = new RestoreSnapshotCommand(snapshots, texts, true);
    }

    @Override
    public CommandSpec spec() {
        return restore.spec();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return restore.execute(context);
    }
}
