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
public final class WhitelistRequireCommand implements ModCommand {

    private final PlayerCommands commands;

    public WhitelistRequireCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-whitelist-require")
                .description("Turn requiring the whitelist to join on or off")
                .arg("state", ArgType.STRING, "on or off");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        String state = PlayerDirectory.clean(context.string("state")).toLowerCase(Locale.ROOT);
        if (!state.equals("on") && !state.equals("off")) {
            return Result.failure(PlayerErrors.badChoice("on, off"));
        }
        return commands.view(context).requireWhitelist(state.equals("on")).map(Reply::of);
    }
}
