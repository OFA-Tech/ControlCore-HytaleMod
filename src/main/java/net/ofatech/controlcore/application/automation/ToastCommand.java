package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ToastCommand implements ModCommand {

    private final Recipients recipients;
    private final NotificationService notifications;
    private final Texts texts;

    public ToastCommand(Recipients recipients, NotificationService notifications, Texts texts) {
        this.recipients = recipients;
        this.notifications = notifications;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-toast")
                .description("Show a small timed message to a player, everyone or the admins")
                .arg("target", ArgType.STRING, "A player's name or UUID, everyone or admins")
                .arg("style", ArgType.STRING, "info, success, warning or error")
                .arg("text", ArgType.GREEDY_STRING, "The text; Title|text for a title");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = texts.forSender(context.sender());
        Optional<ToastLevel> level = ToastLevel.of(context.string("style"));
        if (level.isEmpty()) {
            return Result.failure(Error.validation(
                    "Notify.BadStyle",
                    Text.key("controlcore.errors.players.bad_choice", "Use {choices}.")
                            .with("choices", "info, success, warning, error")));
        }
        return Recipients.text(context.string("text"))
                .flatMap(text -> recipients
                        .resolve(context, context.string("target"))
                        .map(target -> {
                            int bar = text.indexOf('|');
                            String title = bar < 0 ? "" : text.substring(0, bar).strip();
                            String body = (bar < 0 ? text : text.substring(bar + 1)).strip();
                            target.players()
                                    .forEach(player -> notifications.toast(
                                            player, title, body, level.get(), AutomationService.TOAST_SECONDS));
                            return Reply.of(tr.t(
                                    "controlcore.notify.toast_sent",
                                    "target",
                                    target.name(tr),
                                    "count",
                                    target.players().size()));
                        }));
    }
}
