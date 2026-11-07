package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BackupRestoreCommand implements ModCommand {

    private final BackupService backups;
    private final Texts texts;
    private final Messenger messenger;
    private final Log log;

    public BackupRestoreCommand(BackupService backups, Texts texts, Messenger messenger, Log log) {
        this.backups = backups;
        this.texts = texts;
        this.messenger = messenger;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-backup-restore")
                .description("Restore a backup at the next server start")
                .arg("name", ArgType.STRING, "The backup, as /cc-backups lists it")
                .arg("confirm", ArgType.STRING, "Type confirm");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Result<Boolean> confirmed = BackupConfirm.check(context);
        if (confirmed.isFailure()) {
            return Result.failure(confirmed.error());
        }
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        Translator tr = texts.forSender(context.sender());
        CompletableFuture<Result<String>> planned = backups.restoreOnNextStart(context.string("name"), who, tr);
        if (planned.isDone()) {
            return planned.join().map(Reply::of);
        }
        planned.thenAccept(result -> {
            String text = result.isSuccess() ? result.value() : tr.error(result.error());
            who.ifPresentOrElse(player -> messenger.send(player, text), () -> log.info(text));
        });
        return Result.success(Reply.of(tr.t("controlcore.backups.preparing_restore")));
    }
}
