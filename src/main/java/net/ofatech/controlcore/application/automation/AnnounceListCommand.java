package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class AnnounceListCommand implements ModCommand {

    private final AnnouncementService announcements;
    private final Texts texts;

    public AnnounceListCommand(AnnouncementService announcements, Texts texts) {
        this.announcements = announcements;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-announce-list").description("List the rotating announcements and their settings");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        var tr = texts.forSender(context.sender());
        if (!announcements.mayManage(who(context))) {
            return Result.failure(AnnouncementService.forbidden());
        }
        var now = announcements.state();
        StringBuilder text = new StringBuilder(announcements.summary(now, tr));
        if (now.items().isEmpty()) {
            text.append('\n').append(tr.t("controlcore.announcements.empty"));
        }
        for (var item : now.items()) {
            text.append('\n')
                    .append(tr.t(
                            item.enabled() ? "controlcore.announcements.line_on" : "controlcore.announcements.line_off",
                            "id",
                            item.id(),
                            "text",
                            item.text()));
        }
        return Result.success(Reply.of(text.toString()));
    }

    private static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }
}
