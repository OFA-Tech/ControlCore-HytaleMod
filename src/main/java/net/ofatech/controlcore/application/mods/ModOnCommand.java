package net.ofatech.controlcore.application.mods;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ModOnCommand implements ModCommand {

    private final ModCommands commands;

    public ModOnCommand(ModCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-mod-on")
                .description("Turn a mod on from the next start")
                .arg("mod", ArgType.STRING, "The mod's id, name or settings namespace (see /cc-mods)")
                .optionalArg("confirm", ArgType.STRING, "all: also turn on the mods it needs");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.setEnabled(context, true);
    }
}
