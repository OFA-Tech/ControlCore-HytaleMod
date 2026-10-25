package net.ofatech.controlcore.application.bans;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.Bans.Ban;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class BanListCommand implements ModCommand {

    private final BansService bans;
    private final Texts texts;

    public BanListCommand(BansService bans, Texts texts) {
        this.bans = bans;
        this.texts = texts;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-bans").description("List the banned players");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        Optional<PlayerId> who = context.sender().player().map(PlayerInfo::id);
        if (!bans.mayManage(who)) {
            return Result.failure(Error.forbidden(
                    "Bans.Forbidden", Text.key("controlcore.errors.bans.forbidden", "Only admins can manage bans.")));
        }
        Translator tr = texts.forSender(context.sender());
        List<Ban> all = bans.all();
        if (all.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.bans.none")));
        }
        StringBuilder text = new StringBuilder(tr.t("controlcore.bans.list.header", "count", all.size()));
        for (Ban ban : all) {
            text.append('\n')
                    .append(tr.t(
                            "controlcore.bans.list.line",
                            "player",
                            bans.name(ban.target()),
                            "details",
                            bans.details(ban, tr)));
        }
        return Result.success(Reply.of(text.toString()));
    }
}
