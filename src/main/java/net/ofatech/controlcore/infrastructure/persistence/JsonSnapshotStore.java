package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.ports.config.SnapshotStore;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class JsonSnapshotStore implements SnapshotStore {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SUFFIX = ".json";

    private final Path folder;
    private final Log log;

    public JsonSnapshotStore(Path folder, Log log) {
        this.folder = folder;
        this.log = log;
    }

    @Override
    public synchronized List<Snapshot> all() {
        List<Snapshot> snapshots = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return snapshots;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*" + SUFFIX)) {
            for (Path file : stream) {
                read(file).ifPresent(snapshots::add);
            }
        } catch (IOException e) {
            log.error("Could not list snapshots in " + folder, e);
        }
        snapshots.sort(Comparator.comparing(Snapshot::createdAt).reversed());
        return snapshots;
    }

    @Override
    public synchronized Optional<Snapshot> find(String name) {
        Path file = folder.resolve(name + SUFFIX);
        return Files.isRegularFile(file) ? read(file) : Optional.empty();
    }

    @Override
    public synchronized Result<Snapshot> save(Snapshot snapshot) {
        Path file = folder.resolve(snapshot.name() + SUFFIX);
        Path temp = folder.resolve(snapshot.name() + SUFFIX + ".tmp");
        try {
            Files.createDirectories(folder);
            Files.writeString(temp, GSON.toJson(FileForm.of(snapshot)), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Result.success(snapshot);
        } catch (IOException e) {
            log.error("Could not save snapshot " + file, e);
            return Result.failure(Error.failure("Snapshots.WriteFailed", "Could not write " + file.getFileName()));
        }
    }

    @Override
    public synchronized boolean delete(String name) {
        try {
            return Files.deleteIfExists(folder.resolve(name + SUFFIX));
        } catch (IOException e) {
            log.error("Could not delete snapshot " + name, e);
            return false;
        }
    }

    private Optional<Snapshot> read(Path file) {
        String fileName = file.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - SUFFIX.length());
        if (Snapshot.validName(name).isFailure()) {
            log.warn("Skipped snapshot file " + fileName + ": the name must be 1 to 40 letters, digits, - or _");
            return Optional.empty();
        }
        try {
            FileForm form = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileForm.class);
            return Optional.of(form.toSnapshot(name));
        } catch (IOException
                | JsonParseException
                | DateTimeParseException
                | IllegalArgumentException
                | NullPointerException e) {
            log.warn("Skipped unreadable snapshot " + fileName + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static final class Rule {
        private String view;
        private String edit;
    }

    private static final class FileForm {
        private int format = 1;
        private String createdAt;
        private String createdBy;
        private List<String> parts;
        private List<String> mods;
        private Map<String, String> config;
        private Map<String, String> settings;
        private Map<String, String> serverKeys;
        private List<CustomKeyBind> customBinds;
        private Map<String, Rule> permissions;
        private Map<String, Boolean> modsEnabled;
        private Map<String, Map<String, String>> players;
        private boolean profile;
        private boolean partial;

        static FileForm of(Snapshot snapshot) {
            FileForm form = new FileForm();
            form.createdAt = snapshot.createdAt().toString();
            form.createdBy = snapshot.createdBy();
            form.parts = snapshot.parts().stream().map(Snapshot.Part::id).toList();
            form.mods = snapshot.mods().stream().sorted().toList();
            form.config = sorted(snapshot.config());
            form.settings = sorted(snapshot.settings());
            form.serverKeys = sorted(snapshot.serverKeys());
            form.customBinds = snapshot.customBinds();
            form.permissions = new TreeMap<>();
            snapshot.permissions().forEach((target, rule) -> {
                Rule stored = new Rule();
                stored.view = rule.view().orElse(null);
                stored.edit = rule.edit().orElse(null);
                form.permissions.put(target, stored);
            });
            form.modsEnabled = new TreeMap<>(snapshot.modsEnabled());
            form.players = new TreeMap<>();
            snapshot.players().forEach((player, values) -> form.players.put(player, sorted(values)));
            form.profile = snapshot.profile();
            form.partial = snapshot.partial();
            return form;
        }

        Snapshot toSnapshot(String name) {
            Set<Snapshot.Part> partSet = EnumSet.noneOf(Snapshot.Part.class);
            if (parts != null) {
                parts.forEach(text -> Snapshot.Part.of(text).ifPresent(partSet::add));
            }
            Map<String, SettingPermission> rules = new LinkedHashMap<>();
            if (permissions != null) {
                permissions.forEach((target, rule) -> rules.put(
                        target, SettingPermission.INHERIT.withView(rule.view).withEdit(rule.edit)));
            }
            return new Snapshot(
                    name,
                    createdAt == null ? Instant.EPOCH : Instant.parse(createdAt),
                    createdBy,
                    partSet,
                    mods == null ? Set.of() : Set.copyOf(mods),
                    config,
                    settings,
                    serverKeys,
                    customBinds,
                    rules,
                    modsEnabled,
                    players,
                    profile,
                    partial);
        }

        private static Map<String, String> sorted(Map<String, String> values) {
            return new TreeMap<>(values);
        }
    }
}
