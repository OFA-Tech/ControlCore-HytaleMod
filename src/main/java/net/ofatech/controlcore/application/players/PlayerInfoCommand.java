package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView.Details;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class PlayerInfoCommand implements ModCommand {

    private final PlayerCommands commands;

    public PlayerInfoCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-player")
                .description("Show a player's status, groups, whitelist, ban, mute, freeze and recent messages")
                .arg("player", ArgType.STRING, "Player name or UUID; they don't need to be online");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Translator tr = commands.tr(context);
        return commands.onPlayer(
                context, (view, id) -> view.details(id).thenApply(result -> result.map(details -> about(details, tr))));
    }

    private static String about(Details details, Translator tr) {
        int inventory = PlayerCommands.inventoryAt(details, tr);
        return PlayerCommands.text(
                details.title(),
                inventory < 0 ? details.lines() : details.lines().subList(0, inventory));
    }
}
