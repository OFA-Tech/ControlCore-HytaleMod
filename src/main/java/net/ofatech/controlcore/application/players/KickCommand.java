package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class KickCommand implements ModCommand {

    private final PlayerCommands commands;

    public KickCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-kick")
                .description("Kick an online player")
                .arg("player", ArgType.STRING, "Player name or UUID")
                .arg("reason", ArgType.GREEDY_STRING, "Message shown to the player; - for the default");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String reason = context.string("reason").strip();
        PlayerAction.Input input =
                new PlayerAction.Input(context.string("player"), "", reason.equals("-") ? "" : reason, "", "");
        return commands.reply(context, commands.view(context).apply(PlayerAction.KICK, input));
    }
}
