package net.ofatech.controlcore.application.restart;

import com.google.auto.service.AutoService;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class RestartCommand implements ModCommand {

    private final RestartService restart;
    private final Texts texts;

    public RestartCommand(RestartService restart, Texts texts) {
        this.restart = restart;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-restart")
                .description("Restart the server after a countdown")
                .optionalArg("in", ArgType.STRING, "The countdown, for example 30s, 5m or 1h")
                .optionalArg("reason", ArgType.STRING, "Shown to players with the warnings");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        Translator tr = texts.forSender(context.sender());
        String reason = context.arg("reason").map(Object::toString).orElse("");
        return RestartService.delay(context.arg("in").map(Object::toString).orElse(""))
                .flatMap(delay -> restart.start(delay, reason, who, tr))
                .map(Reply::of);
    }
}
