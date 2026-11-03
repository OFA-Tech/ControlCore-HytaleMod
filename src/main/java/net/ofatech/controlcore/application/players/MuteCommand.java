package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class MuteCommand implements ModCommand {

    private final PlayerCommands commands;

    public MuteCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-mute")
                .description("Stop a player from chatting, permanently or for a while")
                .arg("player", ArgType.STRING, "Player name or UUID; they don't need to be online")
                .arg("length", ArgType.STRING, "permanent, or a number with m, h, d or w, for example 30m");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.reply(
                context, commands.view(context).mute(context.string("player"), context.string("length"), true));
    }
}
