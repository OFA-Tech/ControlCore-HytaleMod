package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class FreezeCommand implements ModCommand {

    private final PlayerCommands commands;

    public FreezeCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-freeze")
                .description("Hold a player in place; offline players are frozen when they join")
                .arg("player", ArgType.STRING, "Player name or UUID");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.reply(context, commands.view(context).freeze(context.string("player"), true));
    }
}
