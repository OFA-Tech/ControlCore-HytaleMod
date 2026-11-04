package net.ofatech.controlcore.application.backups;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class UniverseFiles {

    static final String ARCHIVE = "archive";
    private static final Pattern NAME = Pattern.compile("(archive/)?[A-Za-z0-9][A-Za-z0-9._-]{0,120}\\.zip");

    private UniverseFiles() {}

    static Optional<Path> resolve(Path directory, String name) {
        if (name == null || !NAME.matcher(name.strip()).matches() || name.contains("..")) {
            return Optional.empty();
        }
        Path root = directory.toAbsolutePath().normalize();
        Path file = root.resolve(name.strip()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    static long size(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .sum();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    static Path nearestExisting(Path folder) {
        Path current = folder.toAbsolutePath().normalize();
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        return current == null ? folder.toAbsolutePath() : current;
    }

    static void checkEntries(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            if (!entries.hasMoreElements()) {
                throw new IOException("The backup is empty");
            }
            while (entries.hasMoreElements()) {
                inside(root, entries.nextElement().getName());
            }
        }
    }

    static void extract(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path out = inside(root, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = file.getInputStream(entry)) {
                    Files.copy(in, out);
                }
            }
        }
    }

    private static Path inside(Path root, String name) throws IOException {
        Path out = root.resolve(name).normalize();
        if (name.isEmpty() || !out.startsWith(root) || out.equals(root)) {
            throw new IOException("The backup has an entry outside the universe folder: " + name);
        }
        return out;
    }

    static Path asideOf(Path universe, String stamp) {
        Path absolute = universe.toAbsolutePath().normalize();
        return absolute.resolveSibling(absolute.getFileName() + "-before-restore-" + stamp);
    }

    static void restore(Path zip, Path universe, Path aside) throws IOException {
        checkEntries(zip, universe);
        if (Files.exists(aside)) {
            throw new IOException(aside + " already exists");
        }
        boolean had = Files.exists(universe);
        if (had) {
            move(universe, aside);
        }
        try {
            Files.createDirectories(universe);
            extract(zip, universe);
        } catch (IOException | RuntimeException e) {
            try {
                delete(universe);
                if (had) {
                    move(aside, universe);
                }
            } catch (IOException back) {
                e.addSuppressed(back);
            }
            throw e;
        }
    }

    static void move(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    }

    static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
