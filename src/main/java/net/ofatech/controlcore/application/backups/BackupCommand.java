package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import java.util.Optional;
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
public final class BackupCommand implements ModCommand {

    private final BackupService backups;
    private final Texts texts;
    private final Messenger messenger;
    private final Log log;

    public BackupCommand(BackupService backups, Texts texts, Messenger messenger, Log log) {
        this.backups = backups;
        this.texts = texts;
        this.messenger = messenger;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-backup")
                .description("Back up the universe now")
                .optionalArg("directory", ArgType.STRING, "Set the backup directory first, for example backups");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        Translator tr = texts.forSender(context.sender());
        Optional<String> directory = context.arg("directory").map(Object::toString);
        if (directory.isPresent()) {
            if (backups.running()) {
                return Result.failure(BackupService.busy());
            }
            Result<String> set = backups.setDirectory(directory.get(), who, tr);
            if (set.isFailure()) {
                return Result.failure(set.error());
            }
        }
        var started = backups.create(who, tr);
        if (started.isDone() && started.join().isFailure()) {
            return Result.failure(started.join().error());
        }
        started.thenAccept(result -> {
            String text = result.isSuccess()
                    ? tr.t(
                            "controlcore.backups.done",
                            "name",
                            result.value().name(),
                            "size",
                            BackupService.size(result.value().size()))
                    : tr.error(result.error());
            who.ifPresentOrElse(player -> messenger.send(player, text), () -> log.info(text));
        });
        return Result.success(Reply.of(tr.t("controlcore.backups.started")));
    }
}
