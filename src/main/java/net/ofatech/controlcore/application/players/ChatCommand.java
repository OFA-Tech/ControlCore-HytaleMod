package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView.Details;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ChatCommand implements ModCommand {

    private final PlayerCommands commands;

    public ChatCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-chat").description("Show the last 50 chat messages");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Details chat = commands.view(context).chat();
        if (chat.lines().isEmpty()) {
            return Result.failure(PlayerErrors.forbidden());
        }
        return Result.success(Reply.of(PlayerCommands.text(chat.title(), chat.lines())));
    }
}
