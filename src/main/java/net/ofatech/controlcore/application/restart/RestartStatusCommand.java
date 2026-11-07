package net.ofatech.controlcore.application.restart;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RestartStatusCommand implements ModCommand {

    private final RestartService restart;
    private final Texts texts;

    public RestartStatusCommand(RestartService restart, Texts texts) {
        this.restart = restart;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-restart-status")
                .description("Show the restart countdown and the next scheduled restart");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return Result.success(Reply.of(restart.status(texts.forSender(context.sender()))));
    }
}
