package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class WhitelistCommand implements ModCommand {

    private final PlayerCommands commands;

    public WhitelistCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-whitelist")
                .description("List the whitelist (change it with /cc-whitelist-add, -remove and -require)");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayersView view = commands.view(context);
        Translator tr = commands.tr(context);
        if (!view.rights().whitelist()) {
            return Result.failure(PlayerErrors.forbidden());
        }
        List<PlayersView.Entry> listed =
                view.list().stream().filter(PlayersView.Entry::whitelisted).toList();
        if (listed.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.players.whitelist_list.none")));
        }
        StringBuilder text =
                new StringBuilder(tr.t("controlcore.players.whitelist_list.header", "count", listed.size()));
        for (PlayersView.Entry entry : listed.subList(0, Math.min(PlayersListCommand.SHOWN, listed.size()))) {
            text.append('\n')
                    .append(tr.t(
                            "controlcore.players.whitelist_list.line",
                            "player",
                            entry.name().isEmpty() ? entry.id() : entry.name(),
                            "id",
                            entry.id()));
        }
        if (listed.size() > PlayersListCommand.SHOWN) {
            text.append('\n')
                    .append(tr.t(
                            "controlcore.players.whitelist_list.more",
                            "count",
                            listed.size() - PlayersListCommand.SHOWN));
        }
        return Result.success(Reply.of(text.toString()));
    }
}
