package net.ofatech.controlcore.application.backups;

import java.util.Locale;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

final class BackupConfirm {

    static final String WORD = "confirm";

    private BackupConfirm() {}

    static Result<Boolean> check(CommandContext context) {
        if (WORD.equals(context.string("confirm").strip().toLowerCase(Locale.ROOT))) {
            return Result.success(true);
        }
        return Result.failure(Error.validation(
                "Backup.Confirm",
                Text.key("controlcore.errors.backup.confirm", "Type confirm after the backup name to go ahead.")));
    }
}
