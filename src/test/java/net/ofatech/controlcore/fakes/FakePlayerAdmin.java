package net.ofatech.controlcore.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;

public final class FakePlayerAdmin implements PlayerAdmin {

    public final Map<PlayerId, String> modes = new ConcurrentHashMap<>();
    public final List<String> kicked = new ArrayList<>();
    public final List<String> teleports = new ArrayList<>();
    public final Map<PlayerId, List<Item>> inventories = new ConcurrentHashMap<>();

    @Override
    public OptionalInt ping(PlayerId player) {
        return OptionalInt.of(42);
    }

    @Override
    public boolean kick(PlayerId player, String reason) {
        kicked.add(player + ":" + reason);
        return true;
    }

    @Override
    public CompletableFuture<Optional<String>> gameMode(PlayerId player) {
        return CompletableFuture.completedFuture(Optional.of(modes.getOrDefault(player, ADVENTURE)));
    }

    @Override
    public CompletableFuture<Boolean> setGameMode(PlayerId player, String mode) {
        modes.put(player, mode);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Optional<List<Item>>> inventory(PlayerId player) {
        return CompletableFuture.completedFuture(Optional.ofNullable(inventories.get(player)));
    }

    @Override
    public CompletableFuture<Boolean> teleport(PlayerId player, PlayerId to) {
        teleports.add(player + "->" + to);
        return CompletableFuture.completedFuture(true);
    }
}
