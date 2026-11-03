package net.ofatech.controlcore.application.mods;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class ModOffCommand implements ModCommand {

    private final ModCommands commands;

    public ModOffCommand(ModCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-mod-off")
                .description("Turn a mod off from the next start")
                .arg("mod", ArgType.STRING, "The mod's id, name or settings namespace (see /cc-mods)")
                .arg(
                        "confirm",
                        ArgType.STRING,
                        "check: stop if other mods would change; all: also turn off the mods that need it");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        return commands.setEnabled(context, false);
    }
}
