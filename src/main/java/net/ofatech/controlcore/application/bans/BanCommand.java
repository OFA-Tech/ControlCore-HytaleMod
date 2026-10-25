package net.ofatech.controlcore.application.bans;

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
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BanCommand implements ModCommand {

    private final BansService bans;
    private final Texts texts;
    private final Messenger messenger;
    private final Log log;

    public BanCommand(BansService bans, Texts texts, Messenger messenger, Log log) {
        this.bans = bans;
        this.texts = texts;
        this.messenger = messenger;
        this.log = log;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-ban")
                .description("Ban a player, permanently or for a while")
                .arg("player", ArgType.STRING, "Player name or UUID; they don't need to be online")
                .arg("length", ArgType.STRING, "permanent, or a number with m, h, d or w, for example 7d")
                .arg("reason", ArgType.GREEDY_STRING, "Reason shown to the player; - for none");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        Translator tr = texts.forSender(context.sender());
        String reason = context.string("reason").strip();
        String player = context.string("player");
        Result<?> length = BansService.duration(context.string("length"));
        if (length.isFailure()) {
            return Result.failure(length.error());
        }
        bans.ban(who, player, context.string("length"), reason.equals("-") ? "" : reason, tr)
                .thenAccept(result -> {
                    String text = result.isSuccess() ? result.value() : tr.error(result.error());
                    if (who.isPresent()) {
                        messenger.send(who.get(), text, result.isSuccess() ? "#e8a93b" : "#e88a72");
                    } else {
                        log.info(text);
                    }
                });
        return Result.success(Reply.of(tr.t("controlcore.bans.looking_up", "player", player)));
    }
}
