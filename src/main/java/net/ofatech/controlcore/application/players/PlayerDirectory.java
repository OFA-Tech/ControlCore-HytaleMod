package net.ofatech.controlcore.application.players;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.ofatech.controlcore.application.automation.SessionService;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.game.AccessLists;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerDirectory {

    private final Players players;
    private final PlayerData data;
    private final BansService bans;
    private final AccessLists access;

    public PlayerDirectory(Players players, PlayerData data, BansService bans, AccessLists access) {
        this.players = players;
        this.data = data;
        this.bans = bans;
        this.access = access;
    }

    public CompletableFuture<Optional<PlayerId>> find(String nameOrId) {
        String input = clean(nameOrId);
        if (input.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerId> known = local(input);
        return known.isPresent() ? CompletableFuture.completedFuture(known) : bans.lookup(input);
    }

    CompletableFuture<Result<String>> resolve(String player, Function<PlayerId, Result<String>> work) {
        String input = clean(player);
        if (input.isEmpty()) {
            return completed(Result.failure(PlayerErrors.noPlayer()));
        }
        ChangeContext.Attribution attribution = ChangeContext.current();
        return find(input)
                .thenApply(found -> ChangeContext.as(
                        attribution,
                        () -> found.map(work).orElseGet(() -> Result.failure(PlayerErrors.unknown(input)))));
    }

    static <T> CompletableFuture<Result<T>> completed(Result<T> result) {
        return CompletableFuture.completedFuture(result);
    }

    Optional<PlayerId> local(String input) {
        Optional<PlayerId> online = onlineNamed(input);
        if (online.isPresent()) {
            return online;
        }
        Optional<PlayerId> parsed = parse(input);
        if (parsed.isPresent()) {
            return parsed.filter(this::known);
        }
        for (Map.Entry<PlayerId, Map<String, String>> entry : data.all().entrySet()) {
            if (input.equalsIgnoreCase(entry.getValue().get(SessionService.NAME))) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    Optional<PlayerId> onlineNamed(String nameOrId) {
        String input = clean(nameOrId);
        return players.online().stream()
                .filter(info -> info.name().equalsIgnoreCase(input)
                        || info.id().value().toString().equalsIgnoreCase(input))
                .map(PlayerInfo::id)
                .findFirst();
    }

    private boolean known(PlayerId id) {
        return players.find(id).isPresent()
                || data.get(id, SessionService.FIRST_JOIN).isPresent()
                || data.get(id, SessionService.NAME).isPresent()
                || !data.all().getOrDefault(id, Map.of()).isEmpty()
                || access.whitelisted().contains(id)
                || !access.groupsOf(id).isEmpty()
                || bans.find(id).isPresent()
                || bans.knownName(id).isPresent();
    }

    boolean online(PlayerId id) {
        return players.find(id).isPresent();
    }

    public String nameOf(PlayerId id) {
        return players.find(id)
                .map(PlayerInfo::name)
                .or(() -> data.get(id, SessionService.NAME))
                .or(() -> bans.knownName(id))
                .orElse(id.value().toString());
    }

    static Optional<PlayerId> parse(String id) {
        try {
            return Optional.of(PlayerId.of(UUID.fromString(clean(id))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static String clean(String text) {
        return text == null ? "" : text.strip();
    }
}
