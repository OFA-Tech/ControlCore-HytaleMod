package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BringCommand implements ModCommand {

    private final PlayerCommands commands;

    public BringCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-bring")
                .description("Teleport an online player to you")
                .arg("player", ArgType.STRING, "Player name or UUID")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.onPlayer(context, PlayersView::bring);
    }
}
