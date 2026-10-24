package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.ports.config.ChangeLogStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonLinesChangeLog implements ChangeLogStore {

    private static final Gson GSON = new Gson();
    private static final String SUFFIX = ".jsonl";

    private final Path folder;
    private final Log log;

    public JsonLinesChangeLog(Path folder, Log log) {
        this.folder = folder;
        this.log = log;
    }

    @Override
    public synchronized List<ChangeRecord> since(Instant from) {
        YearMonth first = month(from);
        List<ChangeRecord> records = new ArrayList<>();
        for (Path file : files()) {
            Optional<YearMonth> month = monthOf(file);
            if (month.isEmpty() || month.get().isBefore(first)) {
                continue;
            }
            readInto(file, from, records);
        }
        records.sort((a, b) -> Long.compare(a.id(), b.id()));
        return records;
    }

    @Override
    public synchronized void append(ChangeRecord record) {
        Path file = folder.resolve(month(record.at()) + SUFFIX);
        try {
            Files.createDirectories(folder);
            Files.writeString(
                    file,
                    GSON.toJson(Line.of(record)) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Could not add change #" + record.id() + " to " + file, e);
        }
    }

    @Override
    public synchronized void deleteBefore(Instant before) {
        YearMonth keep = month(before);
        for (Path file : files()) {
            Optional<YearMonth> month = monthOf(file);
            if (month.isPresent() && month.get().isBefore(keep)) {
                try {
                    Files.deleteIfExists(file);
                    log.info("Deleted old change history " + file.getFileName());
                } catch (IOException e) {
                    log.warn("Could not delete old change history " + file + ": " + e.getMessage());
                }
            }
        }
    }

    private void readInto(Path file, Instant from, List<ChangeRecord> records) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Could not read change history " + file, e);
            return;
        }
        int skipped = 0;
        for (String text : lines) {
            if (text.isBlank()) {
                continue;
            }
            try {
                Optional<ChangeRecord> record = GSON.fromJson(text, Line.class).toRecord();
                if (record.isEmpty()) {
                    skipped++;
                } else if (!record.get().at().isBefore(from)) {
                    records.add(record.get());
                }
            } catch (JsonParseException | IllegalArgumentException | DateTimeParseException e) {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn("Skipped " + skipped + " unreadable line(s) in " + file);
        }
    }

    private List<Path> files() {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*" + SUFFIX)) {
            stream.forEach(files::add);
        } catch (IOException e) {
            log.error("Could not list change history in " + folder, e);
        }
        files.sort(null);
        return files;
    }

    private static Optional<YearMonth> monthOf(Path file) {
        String name = file.getFileName().toString();
        try {
            return Optional.of(YearMonth.parse(name.substring(0, name.length() - SUFFIX.length())));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    private static YearMonth month(Instant at) {
        return YearMonth.from(at.atZone(ZoneOffset.UTC));
    }

    private static final class Line {
        private long id;
        private String at;
        private String player;
        private String by;
        private String source;
        private String kind;
        private String target;
        private String before;
        private String after;
        private boolean restart;
        private long undoes;

        static Line of(ChangeRecord record) {
            Line line = new Line();
            line.id = record.id();
            line.at = record.at().toString();
            line.player =
                    record.actor().player().map(id -> id.value().toString()).orElse(null);
            line.by = record.actor().name();
            line.source = record.source().name();
            line.kind = record.kind().name();
            line.target = record.target();
            line.before = record.oldValue();
            line.after = record.newValue();
            line.restart = record.restart();
            line.undoes = record.undoes();
            return line;
        }

        Optional<ChangeRecord> toRecord() {
            if (id <= 0 || at == null || source == null || kind == null || target == null) {
                return Optional.empty();
            }
            Optional<PlayerId> who = Optional.ofNullable(player).map(text -> PlayerId.of(UUID.fromString(text)));
            return Optional.of(new ChangeRecord(
                    id,
                    Instant.parse(at),
                    new ChangeRecord.Actor(who, by),
                    ChangeRecord.Source.valueOf(source),
                    ChangeRecord.Kind.valueOf(kind),
                    target,
                    before,
                    after,
                    restart,
                    undoes));
        }
    }
}
