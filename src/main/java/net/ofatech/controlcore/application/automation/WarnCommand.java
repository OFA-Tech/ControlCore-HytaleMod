package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class WarnCommand implements ModCommand {

    private final Recipients recipients;
    private final Texts texts;

    public WarnCommand(Recipients recipients, Texts texts) {
        this.recipients = recipients;
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
        return recipients
                .warn(
                        context.sender().player().map(PlayerInfo::id),
                        context.string("target"),
                        context.integer("seconds"),
                        Optional.empty(),
                        context.string("text"),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
