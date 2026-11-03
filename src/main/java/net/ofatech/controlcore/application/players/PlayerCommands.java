package net.ofatech.controlcore.application.players;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.players.PlayersView.Details;
import net.ofatech.controlcore.domain.model.players.PlayersView.Line;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerCommands {

    private final PlayersService players;
    private final PlayerDirectory directory;
    private final Texts texts;
    private final Messenger messenger;
    private final Log log;

    public PlayerCommands(
            PlayersService players, PlayerDirectory directory, Texts texts, Messenger messenger, Log log) {
        this.players = players;
        this.directory = directory;
        this.texts = texts;
        this.messenger = messenger;
        this.log = log;
    }

    PlayersView view(CommandContext context) {
        return players.viewedBy(who(context));
    }

    boolean maySee(CommandContext context) {
        return players.maySee(who(context));
    }

    Translator tr(CommandContext context) {
        return texts.forSender(context.sender());
    }

    Result<Reply> reply(CommandContext context, CompletableFuture<Result<String>> work) {
        if (work.isDone()) {
            return work.join().map(Reply::of);
        }
        Optional<PlayerId> who = who(context);
        Translator tr = tr(context);
        work.thenAccept(result -> {
            String text = result.isSuccess() ? result.value() : tr.error(result.error());
            if (who.isPresent()) {
                messenger.send(who.get(), text, result.isSuccess() ? "#e8a93b" : "#e88a72");
            } else {
                log.info(text);
            }
        });
        return Result.success(Reply.of(tr.t("controlcore.players.working")));
    }

    Result<Reply> onPlayer(
            CommandContext context, BiFunction<PlayersView, String, CompletableFuture<Result<String>>> work) {
        if (PlayerDirectory.clean(context.string("player")).isEmpty()) {
            return Result.failure(PlayerErrors.noPlayer());
        }
        PlayersView view = view(context);
        return reply(
                context,
                directory.withPlayer(
                        context.string("player"),
                        id -> work.apply(view, id.value().toString())));
    }

    static String text(String title, List<Line> lines) {
        return lines.stream().map(Line::text).collect(Collectors.joining("\n", title + "\n", ""));
    }

    public static int inventoryAt(Details details, Translator tr) {
        String heading = tr.t("controlcore.players.details.inventory");
        List<Line> lines = details.lines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).heading() && lines.get(i).text().equals(heading)) {
                return i;
            }
        }
        return -1;
    }

    private static Optional<PlayerId> who(CommandContext context) {
        return context.sender().player().map(PlayerInfo::id);
    }
}
