package net.ofatech.controlcore.application.backups;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.server.BackupFile;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BackupsCommand implements ModCommand {

    static final int SHOWN = 20;

    private final BackupService backups;
    private final Texts texts;

    public BackupsCommand(BackupService backups, Texts texts) {
        this.backups = backups;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-backups").description("List the backups and a planned restore");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        if (!backups.mayManage(context.sender().player().map(PlayerInfo::id))) {
            return Result.failure(BackupService.forbidden());
        }
        List<String> lines = new ArrayList<>();
        lines.add(backups.status(tr));
        backups.pending().ifPresent(restore -> lines.add(backups.pendingText(restore, tr)));
        List<BackupFile> files = backups.list();
        if (backups.configured() && files.isEmpty()) {
            lines.add(tr.t("controlcore.backups.none"));
        }
        files.stream().limit(SHOWN).forEach(file -> lines.add("- " + backups.describe(file, tr)));
        if (files.size() > SHOWN) {
            lines.add(tr.t("controlcore.backups.more", "count", files.size() - SHOWN));
        }
        return Result.success(Reply.of(String.join("\n", lines)));
    }
}
