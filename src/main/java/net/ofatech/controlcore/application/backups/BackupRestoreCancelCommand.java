package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BackupRestoreCancelCommand implements ModCommand {

    private final BackupService backups;
    private final Texts texts;

    public BackupRestoreCancelCommand(BackupService backups, Texts texts) {
        this.backups = backups;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-backup-restore-cancel")
                .description("Cancel the restore planned for the next start");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return backups.cancelRestore(context.sender().player().map(PlayerInfo::id), texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
