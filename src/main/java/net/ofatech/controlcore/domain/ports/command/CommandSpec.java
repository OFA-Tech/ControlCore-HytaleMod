package net.ofatech.controlcore.domain.ports.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

/**
 * Host-independent description of a chat command.
 *
 * @param permission permission node relative to the mod's base permission; empty means everyone
 */
public record CommandSpec(
        String name,
        String description,
        List<ArgSpec> args,
        Optional<String> permission,
        List<String> aliases,
        boolean playerOnly) {

    public CommandSpec {
        Guard.notBlank(name, "name");
        Guard.notNull(description, "description");
        args = List.copyOf(args);
        Guard.notNull(permission, "permission");
        aliases = List.copyOf(aliases);
    }

    public static CommandSpec named(String name) {
        return new CommandSpec(name, "", List.of(), Optional.empty(), List.of(), false);
    }

    public CommandSpec description(String text) {
        return new CommandSpec(name, text, args, permission, aliases, playerOnly);
    }

    public CommandSpec arg(String argName, ArgType type, String argDescription) {
        return withArg(new ArgSpec(argName, argDescription, type, true));
    }

    public CommandSpec optionalArg(String argName, ArgType type, String argDescription) {
        return withArg(new ArgSpec(argName, argDescription, type, false));
    }

    public CommandSpec permission(String node) {
        return new CommandSpec(name, description, args, Optional.of(Guard.notBlank(node, "node")), aliases, playerOnly);
    }

    public CommandSpec aliases(String... names) {
        return new CommandSpec(name, description, args, permission, List.of(names), playerOnly);
    }

    public CommandSpec requirePlayer() {
        return new CommandSpec(name, description, args, permission, aliases, true);
    }

    private CommandSpec withArg(ArgSpec arg) {
        List<ArgSpec> next = new ArrayList<>(args);
        next.add(arg);
        return new CommandSpec(name, description, next, permission, aliases, playerOnly);
    }
}
