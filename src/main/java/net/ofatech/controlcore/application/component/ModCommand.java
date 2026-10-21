package net.ofatech.controlcore.application.component;

import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

public interface ModCommand {

    CommandSpec spec();

    Result<Reply> execute(CommandContext context);
}
