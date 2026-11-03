package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ToastCommand implements ModCommand {

    private final Recipients recipients;
    private final Texts texts;

    public ToastCommand(Recipients recipients, Texts texts) {
        this.recipients = recipients;
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
        return recipients
                .toast(
                        context.sender().player().map(PlayerInfo::id),
                        context.string("target"),
                        context.string("style"),
                        context.string("text"),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
