package net.ofatech.controlcore.application.backups;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class UniverseFiles {

    static final String ARCHIVE = "archive";
    static final Pattern NAME = Pattern.compile("(archive/)?[A-Za-z0-9][A-Za-z0-9._-]{0,120}\\.zip");
    private static final String WORLDS = "worlds";

    static final class NotUniverse extends IOException {
        private static final long serialVersionUID = 1L;

        NotUniverse(Path zip) {
            super(zip.getFileName() + " is not a Hytale universe backup (it has no worlds folder)");
        }
    }

    record Layout(String prefix, long unpackedSize) {}

    private UniverseFiles() {}

    static Optional<Path> resolve(Path directory, String name) {
        if (name == null || !NAME.matcher(name.strip()).matches() || name.contains("..")) {
            return Optional.empty();
        }
        Path root = directory.toAbsolutePath().normalize();
        Path file = root.resolve(name.strip()).normalize();
        if (!file.startsWith(root)
                || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            if (!file.toRealPath().startsWith(root.toRealPath())) {
                return Optional.empty();
            }
        } catch (IOException e) {
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

    static Layout layout(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        List<String> names = new ArrayList<>();
        long unpacked = 0;
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            if (!entries.hasMoreElements()) {
                throw new IOException("The backup is empty");
            }
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                inside(root, entry.getName());
                names.add(entry.getName().replace('\\', '/'));
                unpacked += Math.max(0, entry.getSize());
            }
        }
        if (hasWorlds(names, "")) {
            return new Layout("", unpacked);
        }
        String top = names.getFirst().split("/", 2)[0];
        String prefix = top + "/";
        boolean shared = !top.isEmpty() && names.stream().allMatch(name -> name.startsWith(prefix));
        if (shared && hasWorlds(names, prefix)) {
            return new Layout(prefix, unpacked);
        }
        throw new NotUniverse(zip);
    }

    private static boolean hasWorlds(List<String> names, String prefix) {
        return names.stream().anyMatch(name -> name.equals(prefix + WORLDS) || name.startsWith(prefix + WORLDS + "/"));
    }

    static void extract(Path zip, Path target, String prefix) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/').substring(prefix.length());
                if (name.isEmpty()) {
                    continue;
                }
                Path out = inside(root, name);
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

    static Path failedOf(Path aside) {
        return aside.resolveSibling(aside.getFileName().toString().replace("-before-restore-", "-failed-restore-"));
    }

    static void restore(Path zip, Path universe, Path aside) throws IOException {
        restore(zip, universe, aside, layout(zip, universe));
    }

    static void restore(Path zip, Path universe, Path aside, Layout layout) throws IOException {
        if (Files.exists(aside)) {
            throw new IOException(aside + " already exists");
        }
        boolean had = Files.exists(universe);
        if (had) {
            move(universe, aside);
        }
        try {
            Files.createDirectories(universe);
            extract(zip, universe, layout.prefix());
        } catch (IOException | RuntimeException e) {
            undo(universe, aside, had, e);
            throw e;
        }
    }

    private static void undo(Path universe, Path aside, boolean had, Exception failure) {
        try {
            delete(universe);
        } catch (IOException e) {
            failure.addSuppressed(e);
            try {
                move(universe, failedOf(aside));
            } catch (IOException moved) {
                failure.addSuppressed(moved);
                return;
            }
        }
        if (!had) {
            return;
        }
        try {
            move(aside, universe);
        } catch (IOException e) {
            failure.addSuppressed(e);
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
