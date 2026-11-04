package net.ofatech.controlcore.domain.ports.game;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ServerBackups {

    Path universe();

    Optional<Path> directory();

    long usableSpace(Path directory);

    CompletableFuture<Void> run();
}
