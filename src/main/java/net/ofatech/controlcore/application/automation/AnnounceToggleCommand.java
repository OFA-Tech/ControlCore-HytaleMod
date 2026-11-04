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
public final class AnnounceToggleCommand implements ModCommand {

    private final AnnouncementService announcements;
    private final Texts texts;

    public AnnounceToggleCommand(AnnouncementService announcements, Texts texts) {
        this.announcements = announcements;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-announce-toggle")
                .description("Turn rotating announcements, or one of them, on or off")
                .arg("state", ArgType.STRING, "on or off")
                .optionalArg("id", ArgType.STRING, "Only this announcement");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String state = context.string("state").strip().toLowerCase(java.util.Locale.ROOT);
        if (!state.equals("on") && !state.equals("off")) {
            return Result.failure(net.ofatech.controlcore.application.text.CommonErrors.badChoice(
                    "Announcements.BadState", "on, off"));
        }
        boolean on = state.equals("on");
        var tr = texts.forSender(context.sender());
        Optional<String> id = context.arg("id").map(Object::toString);
        return (id.isPresent()
                        ? announcements.setItemEnabled(id.get(), on, who(context), tr)
                        : announcements.setSettings(
                                Optional.of(on),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                who(context),
                                tr))
                .map(Reply::of);
    }

    private static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }
}
