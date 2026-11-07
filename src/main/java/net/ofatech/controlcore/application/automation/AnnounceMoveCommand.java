package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class AnnounceMoveCommand implements ModCommand {

    private final AnnouncementService announcements;
    private final Texts texts;

    public AnnounceMoveCommand(AnnouncementService announcements, Texts texts) {
        this.announcements = announcements;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-announce-move")
                .description("Move a rotating announcement up or down the list")
                .arg("id", ArgType.STRING, "The announcement's id, see /cc-announce-list")
                .arg("direction", ArgType.STRING, "up or down");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String direction = context.string("direction").strip().toLowerCase(java.util.Locale.ROOT);
        if (!direction.equals("up") && !direction.equals("down")) {
            return Result.failure(net.ofatech.controlcore.application.text.CommonErrors.badChoice(
                    "Announcements.BadDirection", "up, down"));
        }
        return announcements
                .move(
                        context.string("id"),
                        direction.equals("up") ? -1 : 1,
                        who(context),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }

    private static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }
}
