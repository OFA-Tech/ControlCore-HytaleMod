package net.ofatech.controlcore.domain.ports.command;

import net.ofatech.controlcore.domain.result.Result;

@FunctionalInterface
public interface CommandHandler {

    Result<Reply> handle(CommandContext context);
}
