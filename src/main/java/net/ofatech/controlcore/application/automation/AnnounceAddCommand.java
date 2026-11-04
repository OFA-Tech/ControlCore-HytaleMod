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
public final class AnnounceAddCommand implements ModCommand {

    private final AnnouncementService announcements;
    private final Texts texts;

    public AnnounceAddCommand(AnnouncementService announcements, Texts texts) {
        this.announcements = announcements;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-announce-add")
                .description("Add a rotating announcement")
                .arg("text", ArgType.GREEDY_STRING, "The text; placeholders such as {online} work");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return announcements
                .add(context.string("text"), who(context), texts.forSender(context.sender()))
                .map(Reply::of);
    }

    private static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }
}
