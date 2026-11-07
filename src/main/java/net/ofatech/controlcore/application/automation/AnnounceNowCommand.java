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
public final class AnnounceNowCommand implements ModCommand {

    private final AnnouncementService announcements;
    private final Texts texts;

    public AnnounceNowCommand(AnnouncementService announcements, Texts texts) {
        this.announcements = announcements;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-announce-now")
                .description("Send the next rotating announcement, or one of them, now")
                .optionalArg("id", ArgType.STRING, "This announcement instead of the next one");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return announcements
                .sendNow(context.arg("id").map(Object::toString), who(context), texts.forSender(context.sender()))
                .map(Reply::of);
    }

    private static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }
}
