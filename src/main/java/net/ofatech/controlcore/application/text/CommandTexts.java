package net.ofatech.controlcore.application.text;

import java.util.List;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.command.ArgSpec;
import net.ofatech.controlcore.domain.ports.command.CommandHandler;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class CommandTexts {

    private CommandTexts() {}

    public static CommandSpec spec(CommandSpec spec, Texts texts) {
        String base = "controlcore.commands." + spec.name();
        String description = texts.has(base + ".desc") ? base + ".desc" : spec.description();
        List<ArgSpec> args = spec.args().stream()
                .map(arg -> {
                    String key = base + ".arg." + arg.name();
                    return texts.has(key) ? new ArgSpec(arg.name(), key, arg.type(), arg.required()) : arg;
                })
                .toList();
        return new CommandSpec(spec.name(), description, args, spec.permission(), spec.aliases(), spec.playerOnly());
    }

    public static CommandHandler handler(CommandSpec spec, CommandHandler handler, Texts texts) {
        return context -> {
            Result<Reply> result = spec.playerOnly() && !context.sender().isPlayer()
                    ? Result.failure(Error.validation(
                            "Commands.PlayersOnly",
                            Text.key("controlcore.commands.players_only", "Only players can use this command.")))
                    : handler.handle(context);
            if (result.isSuccess()) {
                return result;
            }
            Error error = result.error();
            String text = texts.forSender(context.sender()).error(error);
            return Result.failure(new Error(error.code(), text, error.type()));
        };
    }
}
