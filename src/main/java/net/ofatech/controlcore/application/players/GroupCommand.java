package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import java.util.Locale;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class GroupCommand implements ModCommand {

    private final PlayerCommands commands;

    public GroupCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-group")
                .description("Add a player to a permission group, remove them from it, or make it their only group")
                .arg("action", ArgType.STRING, "add, remove or set (only this group)")
                .arg("player", ArgType.STRING, "Player name or UUID; they don't need to be online")
                .arg("group", ArgType.STRING, "Group id, as shown by /cc-groups");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String action = context.string("action").strip().toLowerCase(Locale.ROOT);
        PlayersView view = commands.view(context);
        return switch (action) {
            case "add", "remove" ->
                commands.reply(
                        context, view.group(context.string("player"), context.string("group"), action.equals("add")));
            case "set" -> commands.reply(context, view.onlyGroup(context.string("player"), context.string("group")));
            default -> Result.failure(PlayerErrors.badChoice("add, remove, set"));
        };
    }
}
