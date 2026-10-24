package net.ofatech.controlcore.application.history;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RollbackCommand implements ModCommand {

    private final HistoryEditor history;
    private final Texts texts;
    private final Log log;

    public RollbackCommand(HistoryEditor history, Texts texts, Log log) {
        this.history = history;
        this.texts = texts;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-rollback")
                .description("Put back the value a change replaced (see /cc-history for the numbers)")
                .arg("change", ArgType.INTEGER, "The change number, for example 12");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        int id = context.integer("change");
        Actor actor = context.sender()
                .player()
                .map(player -> Actor.player(player.id(), player.name()))
                .orElseGet(() -> Actor.console(context.sender().name()));
        return history.rollback(
                        id, context.sender().player().map(PlayerInfo::id), actor, texts.forSender(context.sender()))
                .map(text -> {
                    log.info(context.sender().name() + " rolled back change #" + id);
                    return Reply.of(text);
                });
    }
}
