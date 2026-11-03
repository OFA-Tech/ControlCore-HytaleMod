package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class WhitelistRemoveCommand implements ModCommand {

    private final PlayerCommands commands;

    public WhitelistRemoveCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-whitelist-remove")
                .description("Remove a player from the whitelist")
                .arg("player", ArgType.STRING, "Player name or UUID");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.whitelist(context, false);
    }
}
