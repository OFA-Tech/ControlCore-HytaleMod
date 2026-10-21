package net.ofatech.controlcore.infrastructure.platforms.hytale.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.Argument;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.command.ArgSpec;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandHandler;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Ids;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Messages;

final class SpecCommand extends AbstractCommand {

    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    private final CommandSpec spec;
    private final CommandHandler handler;
    private final Log log;
    private final Map<ArgSpec, Argument<?, ?>> arguments = new LinkedHashMap<>();

    @SuppressWarnings("this-escape")
    SpecCommand(CommandSpec spec, CommandHandler handler, String basePermission, Log log) {
        super(spec.name(), spec.description());
        this.spec = spec;
        this.handler = handler;
        this.log = log;
        for (ArgSpec arg : spec.args()) {
            arguments.put(arg, declare(arg, argumentType(arg.type())));
        }
        if (!spec.aliases().isEmpty()) {
            addAliases(spec.aliases().toArray(String[]::new));
        }
        spec.permission()
                .ifPresentOrElse(node -> requirePermission(basePermission + "." + node), this::requireNoPermission);
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        Optional<PlayerInfo> player = senderPlayer(context);
        Result<Reply> result;
        try {
            result = handler.handle(new TranslatedContext(senderOf(context, player), values(context)));
        } catch (RuntimeException e) {
            log.error("Command /" + spec.name() + " failed", e);
            context.sendMessage(Messages.error("Something went wrong while running this command."));
            return DONE;
        }
        result.onSuccess(reply -> reply.text().ifPresent(text -> context.sendMessage(Messages.text(text))))
                .onFailure(error -> context.sendMessage(Messages.error(error)));
        return DONE;
    }

    private Map<String, Object> values(CommandContext context) {
        Map<String, Object> values = new HashMap<>();
        arguments.forEach((arg, argument) -> {
            if (context.provided(argument)) {
                Object value = context.get(argument);
                values.put(arg.name(), value instanceof PlayerRef ref ? Ids.player(ref) : value);
            }
        });
        return values;
    }

    private Argument<?, ?> declare(ArgSpec arg, ArgumentType<?> type) {
        return arg.required()
                ? withRequiredArg(arg.name(), arg.description(), type)
                : withOptionalArg(arg.name(), arg.description(), type);
    }

    private static ArgumentType<?> argumentType(ArgType type) {
        return switch (type) {
            case STRING -> ArgTypes.STRING;
            case GREEDY_STRING -> ArgTypes.GREEDY_STRING;
            case INTEGER -> ArgTypes.INTEGER;
            case DECIMAL -> ArgTypes.DOUBLE;
            case BOOLEAN -> ArgTypes.BOOLEAN;
            case PLAYER -> ArgTypes.PLAYER_REF;
        };
    }

    private static Optional<PlayerInfo> senderPlayer(CommandContext context) {
        if (!context.isPlayer()) {
            return Optional.empty();
        }
        CommandSender sender = context.sender();
        return Optional.of(new PlayerInfo(PlayerId.of(sender.getUuid()), sender.getUsername()));
    }

    private static net.ofatech.controlcore.domain.ports.command.CommandSender senderOf(
            CommandContext context, Optional<PlayerInfo> player) {
        return player.map(net.ofatech.controlcore.domain.ports.command.CommandSender::of)
                .orElseGet(() -> net.ofatech.controlcore.domain.ports.command.CommandSender.console(
                        context.sender().getUsername()));
    }

    private record TranslatedContext(
            net.ofatech.controlcore.domain.ports.command.CommandSender sender, Map<String, Object> values)
            implements net.ofatech.controlcore.domain.ports.command.CommandContext {

        @Override
        public Optional<Object> arg(String name) {
            return Optional.ofNullable(values.get(name));
        }
    }
}
