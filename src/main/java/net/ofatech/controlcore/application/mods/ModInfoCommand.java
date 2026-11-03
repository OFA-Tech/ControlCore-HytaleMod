package net.ofatech.controlcore.application.mods;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ModInfoCommand implements ModCommand {

    private final ModCommands commands;

    public ModInfoCommand(ModCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-mod-info")
                .description("Show what a mod needs, can use and is needed by")
                .arg("mod", ArgType.STRING, "The mod's id, name or settings namespace (see /cc-mods)");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.details(context, context.string("mod"));
    }
}
