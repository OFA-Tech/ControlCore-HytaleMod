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
public final class AnnounceSetCommand implements ModCommand {

    private final AnnouncementService announcements;
    private final Texts texts;

    public AnnounceSetCommand(AnnouncementService announcements, Texts texts) {
        this.announcements = announcements;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-announce-set")
                .description("Change how rotating announcements are sent")
                .optionalArg("interval", ArgType.STRING, "Minutes between two announcements, at least 1")
                .optionalArg("order", ArgType.STRING, "in-turn or random")
                .optionalArg("channel", ArgType.STRING, "chat, toast or warning")
                .optionalArg("min-players", ArgType.STRING, "Send only with at least this many players online");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return announcements
                .setSettings(
                        Optional.empty(),
                        text(context, "interval"),
                        text(context, "order"),
                        text(context, "channel"),
                        text(context, "min-players"),
                        who(context),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }

    private static Optional<String> text(CommandContext context, String name) {
        return context.arg(name).map(Object::toString);
    }

    private static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }
}
