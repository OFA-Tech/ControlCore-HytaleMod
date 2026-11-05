package net.ofatech.controlcore.fakes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.ofatech.controlcore.domain.ports.game.ServerBackups;

public final class FakeServerBackups implements ServerBackups {

    public volatile Path universe = Path.of("build", "no-universe");
    public volatile Optional<Path> directory = Optional.empty();
    public volatile long usable = Long.MAX_VALUE;
    public volatile CompletableFuture<Void> next;
    public volatile CompletableFuture<Void> spaceGate;
    public final AtomicInteger runs = new AtomicInteger();

    @Override
    public Path universe() {
        return universe;
    }

    @Override
    public Optional<Path> directory() {
        return directory;
    }

    @Override
    public long usableSpace(Path folder) {
        CompletableFuture<Void> gate = spaceGate;
        if (gate != null) {
            gate.join();
        }
        return usable;
    }

    @Override
    public CompletableFuture<Void> run() {
        runs.incrementAndGet();
        if (next != null) {
            return next;
        }
        try {
            Path dir = directory.orElseThrow();
            Files.createDirectories(dir);
            zip(dir.resolve("2026-01-0" + runs.get() + "_10-00-00.zip"), "worlds/default/config.json", "{}");
            return CompletableFuture.completedFuture(null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void zip(Path file, String... namesAndTexts) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i + 1 < namesAndTexts.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndTexts[i]));
                out.write(namesAndTexts[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}
