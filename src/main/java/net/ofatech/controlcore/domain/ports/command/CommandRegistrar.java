package net.ofatech.controlcore.domain.ports.command;

public interface CommandRegistrar {

    void register(CommandSpec spec, CommandHandler handler);
}
