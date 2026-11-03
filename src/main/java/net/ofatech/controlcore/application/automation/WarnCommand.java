package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class WarnCommand implements ModCommand {

    private final Recipients recipients;
    private final NotificationService notifications;
    private final Texts texts;

    public WarnCommand(Recipients recipients, NotificationService notifications, Texts texts) {
        this.recipients = recipients;
        this.notifications = notifications;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-warn")
                .description("Show a warning bar to a player, everyone or the admins until it runs out or is cleared")
                .arg("target", ArgType.STRING, "A player's name or UUID, everyone or admins")
                .arg("seconds", ArgType.INTEGER, "How long it stays; 0 until /cc-warn-clear")
                .arg(
                        "text",
                        ArgType.GREEDY_STRING,
                        "The text; start with info:, success:, warning: or error: for a style");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        int seconds = context.integer("seconds");
        if (seconds < 0 || seconds > Recipients.MAX_WARNING_SECONDS) {
            return Result.failure(Error.validation(
                    "Notify.InvalidSeconds",
                    Text.key("controlcore.errors.notify.invalid_seconds", "Use 0 to {max} seconds.")
                            .with("max", Recipients.MAX_WARNING_SECONDS)));
        }
        return Recipients.text(context.string("text"))
                .flatMap(text -> recipients
                        .resolve(context, context.string("target"))
                        .map(target -> {
                            AutomationService.Leveled leveled = AutomationService.Leveled.of(text);
                            if (target.kind() == Recipients.Kind.EVERYONE) {
                                notifications.warningAll(
                                        Recipients.WARNING_ID, leveled.text(), leveled.level(), seconds);
                            } else {
                                target.players()
                                        .forEach(player -> notifications.warning(
                                                player,
                                                Recipients.WARNING_ID,
                                                leveled.text(),
                                                leveled.level(),
                                                seconds));
                            }
                            return Reply.of(tr.t(
                                    "controlcore.notify.warning_sent",
                                    "target",
                                    target.name(tr),
                                    "count",
                                    target.players().size()));
                        }));
    }
}
