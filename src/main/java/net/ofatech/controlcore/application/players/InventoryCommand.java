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
public final class InventoryCommand implements ModCommand {

    private final PlayerCommands commands;

    public InventoryCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-inventory")
                .description("Show what an online player is carrying")
                .arg("player", ArgType.STRING, "Player name or UUID");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        if (!commands.view(context).rights().inventory()) {
            return Result.failure(PlayerErrors.forbidden());
        }
        Translator tr = commands.tr(context);
        return commands.onPlayer(
                context,
                (view, id) -> view.details(id).thenApply(result -> result.flatMap(details -> items(details, tr))));
    }

    private static Result<String> items(Details details, Translator tr) {
        int inventory = PlayerCommands.inventoryAt(details, tr);
        if (inventory < 0) {
            return Result.failure(PlayerErrors.protectedAdmin(details.title()));
        }
        return Result.success(PlayerCommands.text(
                details.title(),
                details.lines().subList(inventory, details.lines().size())));
    }
}
