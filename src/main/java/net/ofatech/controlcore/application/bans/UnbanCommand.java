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
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class UnbanCommand implements ModCommand {

    private final BansService bans;
    private final Texts texts;

    public UnbanCommand(BansService bans, Texts texts) {
        this.bans = bans;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-unban")
                .description("Lift a player's ban")
                .arg("player", ArgType.STRING, "Player name or UUID, as shown by /cc-bans");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        Translator tr = texts.forSender(context.sender());
        return bans.unban(who, context.string("player"), tr).map(Reply::of);
    }
}
