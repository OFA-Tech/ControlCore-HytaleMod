package net.ofatech.controlcore.application.restart;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RestartCancelCommand implements ModCommand {

    private final RestartService restart;
    private final Texts texts;

    public RestartCancelCommand(RestartService restart, Texts texts) {
        this.restart = restart;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-restart-cancel").description("Cancel the restart countdown");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return restart.cancel(context.sender().player().map(PlayerInfo::id), texts.forSender(context.sender()))
                .map(Reply::of);
    }
}
