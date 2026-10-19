package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandHandler;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.command.CommandSender;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

public final class FakeCommandRegistrar implements CommandRegistrar {

    public record Registered(CommandSpec spec, CommandHandler handler) {}

    public final Map<String, Registered> commands = new ConcurrentHashMap<>();

    @Override
    public void register(CommandSpec spec, CommandHandler handler) {
        if (commands.putIfAbsent(spec.name(), new Registered(spec, handler)) != null) {
            throw new IllegalStateException("Duplicate command " + spec.name());
        }
    }

    public Result<Reply> run(String name, PlayerInfo sender, Map<String, Object> args) {
        CommandSender who = CommandSender.of(sender);
        CommandContext context = new CommandContext() {
            @Override
            public CommandSender sender() {
                return who;
            }

            @Override
            public Optional<Object> arg(String argName) {
                return Optional.ofNullable(args.get(argName));
            }
        };
        return commands.get(name).handler().handle(context);
    }
}
