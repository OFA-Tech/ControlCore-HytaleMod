package net.ofatech.controlcore.domain.ports.command;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerInfo;

/** One invocation of a command: the sender and the parsed arguments. */
public interface CommandContext {

    CommandSender sender();

    /** Value of an argument, already converted to {@link ArgType#valueType()}; empty if not given. */
    Optional<Object> arg(String name);

    default <T> T required(String name, Class<T> type) {
        return arg(name).map(type::cast).orElseThrow(() -> new IllegalStateException("Missing argument: " + name));
    }

    default String string(String name) {
        return required(name, String.class);
    }

    default int integer(String name) {
        return required(name, Integer.class);
    }

    default double decimal(String name) {
        return required(name, Double.class);
    }

    default boolean bool(String name) {
        return required(name, Boolean.class);
    }

    default PlayerInfo player(String name) {
        return required(name, PlayerInfo.class);
    }
}
