package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Locale;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
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
                .description("Add a player to the whitelist, remove one, list it, or turn requiring it on or off")
                .arg("action", ArgType.STRING, "add, remove, list or require")
                .optionalArg("player", ArgType.STRING, "Player name or UUID for add and remove; on or off for require");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayersView view = commands.view(context);
        String action = context.string("action").strip().toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            return list(view, commands.tr(context));
        }
        String player = context.arg("player").map(String.class::cast).orElse("");
        if (action.equals("require")) {
            String state = PlayerDirectory.clean(player).toLowerCase(Locale.ROOT);
            if (!state.equals("on") && !state.equals("off")) {
                return Result.failure(PlayerErrors.badChoice("on, off"));
            }
            return view.requireWhitelist(state.equals("on")).map(Reply::of);
        }
        if (!action.equals("add") && !action.equals("remove")) {
            return Result.failure(PlayerErrors.badChoice("add, remove, list, require"));
        }
        if (PlayerDirectory.clean(player).isEmpty()) {
            return Result.failure(PlayerErrors.noPlayer());
        }
        return commands.reply(context, view.whitelist(player, action.equals("add")));
    }

    private static Result<Reply> list(PlayersView view, Translator tr) {
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
        for (PlayersView.Entry entry : listed) {
            text.append('\n')
                    .append(tr.t(
                            "controlcore.players.whitelist_list.line",
                            "player",
                            entry.name().isEmpty() ? entry.id() : entry.name(),
                            "id",
                            entry.id()));
        }
        return Result.success(Reply.of(text.toString()));
    }
}
