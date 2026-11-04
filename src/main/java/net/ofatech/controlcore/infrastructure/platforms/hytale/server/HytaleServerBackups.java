package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.ports.game.ServerBackups;

public final class HytaleServerBackups implements ServerBackups {

    @Override
    public Path universe() {
        return Constants.UNIVERSE_PATH;
    }

    @Override
    public Optional<Path> directory() {
        HytaleServer server = HytaleServer.get();
        return server == null
                ? Optional.empty()
                : Optional.ofNullable(server.getConfig().getBackupConfig().getDirectory());
    }

    @Override
    public long usableSpace(Path directory) {
        try {
            return Files.getFileStore(directory).getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public CompletableFuture<Void> run() {
        Universe universe = Universe.get();
        if (universe == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("The universe is not loaded yet"));
        }
        return universe.runBackup();
    }
}
