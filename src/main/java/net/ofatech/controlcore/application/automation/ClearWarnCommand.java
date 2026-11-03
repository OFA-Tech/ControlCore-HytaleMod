package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ClearWarnCommand implements ModCommand {

    private final Recipients recipients;
    private final NotificationService notifications;
    private final Texts texts;

    public ClearWarnCommand(Recipients recipients, NotificationService notifications, Texts texts) {
        this.recipients = recipients;
        this.notifications = notifications;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-warn-clear")
                .description("Take away the warning bar shown with /cc-warn")
                .arg("target", ArgType.STRING, "A player's name or UUID, everyone or admins");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        return recipients.resolve(context, context.string("target")).map(target -> {
            if (target.kind() == Recipients.Kind.EVERYONE) {
                notifications.clearWarningAll(Recipients.WARNING_ID);
            } else {
                target.players().forEach(player -> notifications.clearWarning(player, Recipients.WARNING_ID));
            }
            return Reply.of(tr.t("controlcore.notify.warning_cleared", "target", target.name(tr)));
        });
    }
}
