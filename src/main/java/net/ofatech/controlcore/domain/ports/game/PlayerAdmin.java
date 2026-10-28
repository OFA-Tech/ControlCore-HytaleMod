package net.ofatech.controlcore.domain.ports.game;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface PlayerAdmin {

    String ADVENTURE = "adventure";
    String CREATIVE = "creative";

    OptionalInt ping(PlayerId player);

    boolean kick(PlayerId player, String reason);

    CompletableFuture<Optional<String>> gameMode(PlayerId player);

    CompletableFuture<Boolean> setGameMode(PlayerId player, String mode);

    CompletableFuture<Boolean> teleport(PlayerId player, PlayerId to);
}
