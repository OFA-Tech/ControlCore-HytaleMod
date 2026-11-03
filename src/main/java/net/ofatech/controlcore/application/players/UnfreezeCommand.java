package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class UnfreezeCommand implements ModCommand {

    private final PlayerCommands commands;

    public UnfreezeCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-unfreeze")
                .description("Let a frozen player move again")
                .arg("player", ArgType.STRING, "Player name or UUID");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.reply(context, commands.view(context).freeze(context.string("player"), false));
    }
}
