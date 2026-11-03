package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import java.util.Locale;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class GroupPermissionCommand implements ModCommand {

    private final PlayerCommands commands;

    public GroupPermissionCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-group-perm")
                .description("Give a permission group a permission node or take one away")
                .arg("action", ArgType.STRING, "give or take")
                .arg("group", ArgType.STRING, "Group id, as shown by /cc-groups")
                .arg("node", ArgType.STRING, "Permission node, for example homes.use");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String action = context.string("action").strip().toLowerCase(Locale.ROOT);
        if (!action.equals("give") && !action.equals("take")) {
            return Result.failure(PlayerErrors.badChoice("give, take"));
        }
        return commands.view(context)
                .groupPermission(context.string("group"), context.string("node"), action.equals("give"))
                .map(Reply::of);
    }
}
