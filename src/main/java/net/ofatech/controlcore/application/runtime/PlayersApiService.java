package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.ofatech.controlcore.api.PlayersApi;
import net.ofatech.controlcore.application.players.PlayerCommands;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.players.PlayerErrors;
import net.ofatech.controlcore.application.players.PlayersService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(PlayersApi.class)
public final class PlayersApiService implements PlayersApi {

    private final PlayersService players;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public PlayersApiService(PlayersService players, Texts texts, PlayerDirectory directory) {
        this(players, texts, directory, ApiActor.MOD);
    }

    private PlayersApiService(PlayersService players, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.players = players;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public PlayersApi actingFor(PlayerId player) {
        return new PlayersApiService(players, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public Rights rights() {
        PlayersView.Rights rights = view().rights();
        return new Rights(
                rights.kick(),
                rights.gameMode(),
                rights.teleport(),
                rights.ban(),
                rights.whitelist(),
                rights.groups(),
                rights.mute(),
                rights.freeze(),
                rights.inventory());
    }

    @Override
    public List<Player> list(Filter filter, String search) {
        PlayersView.Filter wanted = PlayersView.Filter.valueOf((filter == null ? Filter.ALL : filter).name());
        return view().list().stream()
                .filter(entry -> entry.matches(wanted, search))
                .map(entry -> new Player(
                        PlayerId.of(UUID.fromString(entry.id())),
                        entry.name(),
                        entry.online(),
                        entry.banned(),
                        entry.whitelisted(),
                        entry.operator(),
                        entry.muted(),
                        entry.frozen(),
                        entry.status(),
                        entry.ban()))
                .toList();
    }

    @Override
    public CompletableFuture<Result<Details>> details(String player) {
        return onPlayer(player, PlayersView::details).thenApply(result -> result.map(PlayersApiService::details));
    }

    @Override
    public CompletableFuture<Result<List<String>>> inventory(String player) {
        if (!view().rights().inventory()) {
            return CompletableFuture.completedFuture(Result.failure(PlayerErrors.forbidden()));
        }
        return onPlayer(player, PlayersView::details)
                .thenApply(result -> result.flatMap(details -> {
                    int at = PlayerCommands.inventoryAt(details, texts.forPlayer(actor.who()));
                    if (at < 0) {
                        return Result.failure(PlayerErrors.protectedAdmin(details.title()));
                    }
                    return Result.success(
                            details.lines().subList(at + 1, details.lines().size()).stream()
                                    .map(PlayersView.Line::text)
                                    .toList());
                }));
    }

    @Override
    public List<String> recentChat() {
        return view().chat().lines().stream().map(PlayersView.Line::text).toList();
    }

    @Override
    public CompletableFuture<Result<String>> kick(String player, String reason) {
        return apply(PlayerAction.KICK, player, "", reason, "", "");
    }

    @Override
    public CompletableFuture<Result<String>> ban(String player, String length, String reason) {
        return apply(PlayerAction.BAN, player, length, reason, "", "");
    }

    @Override
    public CompletableFuture<Result<String>> unban(String player) {
        return apply(PlayerAction.UNBAN, player, "", "", "", "");
    }

    @Override
    public CompletableFuture<Result<String>> mute(String player, String length) {
        return apply(PlayerAction.MUTE_ADD, player, length, "", "", "");
    }

    @Override
    public CompletableFuture<Result<String>> unmute(String player) {
        return apply(PlayerAction.MUTE_REMOVE, player, "", "", "", "");
    }

    @Override
    public CompletableFuture<Result<String>> freeze(String player) {
        return apply(PlayerAction.FREEZE_ADD, player, "", "", "", "");
    }

    @Override
    public CompletableFuture<Result<String>> unfreeze(String player) {
        return apply(PlayerAction.FREEZE_REMOVE, player, "", "", "", "");
    }

    @Override
    public CompletableFuture<Result<String>> toggleGameMode(String player) {
        return onPlayer(player, PlayersView::toggleGameMode);
    }

    @Override
    public CompletableFuture<Result<String>> teleportTo(String player) {
        return onPlayer(player, PlayersView::teleportTo);
    }

    @Override
    public CompletableFuture<Result<String>> bring(String player) {
        return onPlayer(player, PlayersView::bring);
    }

    @Override
    public CompletableFuture<Result<String>> whitelist(String player, boolean add) {
        return apply(add ? PlayerAction.WHITELIST_ADD : PlayerAction.WHITELIST_REMOVE, player, "", "", "", "");
    }

    @Override
    public boolean whitelistRequired() {
        return view().whitelistRequired();
    }

    @Override
    public Result<String> requireWhitelist(boolean on) {
        return actor.run(() -> view().requireWhitelist(on));
    }

    @Override
    public CompletableFuture<Result<String>> operator(String player, boolean add) {
        return apply(add ? PlayerAction.OP_ADD : PlayerAction.OP_REMOVE, player, "", "", "", "");
    }

    @Override
    public List<String> groups() {
        return view().groups().stream().map(PlayersView.Choice::id).toList();
    }

    @Override
    public CompletableFuture<Result<String>> group(String player, String group, boolean add) {
        return apply(add ? PlayerAction.GROUP_ADD : PlayerAction.GROUP_REMOVE, player, "", "", group, "");
    }

    @Override
    public CompletableFuture<Result<String>> setOnlyGroup(String player, String group) {
        return apply(PlayerAction.GROUP_SET, player, "", "", group, "");
    }

    @Override
    public List<String> groupPermissions(String group) {
        return view().groupPermissions(group);
    }

    @Override
    public Result<String> groupPermission(String group, String node, boolean add) {
        return actor.run(() -> view().groupPermission(group, node, add));
    }

    private PlayersView view() {
        return players.viewedBy(actor.who());
    }

    private CompletableFuture<Result<String>> apply(
            PlayerAction action, String player, String length, String reason, String group, String node) {
        PlayerAction.Input input = new PlayerAction.Input(player, length, reason, group, node);
        return actor.run(() -> view().apply(action, input));
    }

    private <T> CompletableFuture<Result<T>> onPlayer(
            String player, BiFunction<PlayersView, String, CompletableFuture<Result<T>>> work) {
        PlayersView view = view();
        Function<PlayerId, CompletableFuture<Result<T>>> onId =
                id -> work.apply(view, id.value().toString());
        return actor.run(() -> directory.withPlayer(player, onId));
    }

    private static Details details(PlayersView.Details details) {
        return new Details(
                details.title(),
                details.lines().stream()
                        .map(line -> new Line(line.text(), line.heading()))
                        .toList());
    }
}
