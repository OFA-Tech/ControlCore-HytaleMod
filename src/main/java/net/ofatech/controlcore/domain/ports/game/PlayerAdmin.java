package net.ofatech.controlcore.domain.ports.game;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface PlayerAdmin {

    String ADVENTURE = "adventure";
    String CREATIVE = "creative";

    record Item(String id, int quantity) {}

    record Spot(String world, double x, double y, double z) {}

    OptionalInt ping(PlayerId player);

    boolean kick(PlayerId player, String reason);

    CompletableFuture<Optional<String>> gameMode(PlayerId player);

    CompletableFuture<Boolean> setGameMode(PlayerId player, String mode);

    CompletableFuture<Boolean> teleport(PlayerId player, PlayerId to);

    CompletableFuture<Optional<List<Item>>> inventory(PlayerId player);

    CompletableFuture<Optional<Spot>> spot(PlayerId player);

    CompletableFuture<Boolean> keepAt(PlayerId player, Spot spot);
}
