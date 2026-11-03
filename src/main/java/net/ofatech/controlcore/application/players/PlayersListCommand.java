package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.players.PlayersView.Filter;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class PlayersListCommand implements ModCommand {

    static final int SHOWN = 20;
    static final String CHOICES = "everyone, online, offline, banned, whitelisted, operators, muted, frozen";

    private final PlayerCommands commands;

    public PlayersListCommand(PlayerCommands commands) {
        this.commands = commands;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-players")
                .description("List players like the Players tab, with an optional filter and search")
                .optionalArg("filter", ArgType.STRING, CHOICES)
                .optionalArg("search", ArgType.STRING, "Only players whose name or UUID contains this text");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        if (!commands.maySee(context)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String wanted = context.arg("filter")
                .map(Object::toString)
                .map(text -> text.strip().toLowerCase(Locale.ROOT))
                .orElse("everyone");
        Optional<Filter> filter = wanted.equals("everyone")
                ? Optional.of(Filter.ALL)
                : Arrays.stream(Filter.values())
                        .filter(found -> found.id().equals(wanted))
                        .findFirst();
        if (filter.isEmpty()) {
            return Result.failure(PlayerErrors.badChoice(CHOICES));
        }
        String search = context.arg("search").map(Object::toString).orElse("");
        Translator tr = commands.tr(context);
        List<PlayersView.Entry> matching = commands.view(context).list().stream()
                .filter(entry -> entry.matches(filter.get(), search))
                .toList();
        if (matching.isEmpty()) {
            return Result.success(Reply.of(tr.t("controlcore.players.list.none")));
        }
        StringBuilder text = new StringBuilder(tr.t(
                "controlcore.players.list.header",
                "filter",
                tr.t("controlcore.players.filter." + filter.get().id()),
                "count",
                matching.size()));
        for (PlayersView.Entry entry : matching.subList(0, Math.min(SHOWN, matching.size()))) {
            text.append('\n').append(line(entry, tr));
        }
        if (matching.size() > SHOWN) {
            text.append('\n').append(tr.t("controlcore.players.list.more", "count", matching.size() - SHOWN));
        }
        return Result.success(Reply.of(text.toString()));
    }

    private static String line(PlayersView.Entry entry, Translator tr) {
        String badges = Stream.of(
                        entry.banned() ? "banned" : "",
                        entry.whitelisted() ? "whitelisted" : "",
                        entry.operator() ? "operator" : "",
                        entry.muted() ? "muted" : "",
                        entry.frozen() ? "frozen" : "")
                .filter(badge -> !badge.isEmpty())
                .map(badge -> " [" + tr.t("controlcore.ui.players." + badge) + "]")
                .collect(Collectors.joining());
        return tr.t(
                "controlcore.players.list.line",
                "player",
                entry.name().isEmpty() ? "?" : entry.name(),
                "id",
                entry.id().substring(0, Math.min(8, entry.id().length())),
                "badges",
                badges,
                "status",
                entry.status());
    }
}
