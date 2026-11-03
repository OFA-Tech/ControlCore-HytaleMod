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
public final class ClearWarnCommand implements ModCommand {

    private final Recipients recipients;
    private final Texts texts;

    public ClearWarnCommand(Recipients recipients, Texts texts) {
        this.recipients = recipients;
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
        return recipients
                .clear(
                        context.sender().player().map(PlayerInfo::id),
                        context.string("target"),
                        texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
