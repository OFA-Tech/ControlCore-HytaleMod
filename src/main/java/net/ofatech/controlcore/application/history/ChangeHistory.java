package net.ofatech.controlcore.application.history;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.ports.config.ChangeLogStore;
import net.ofatech.controlcore.domain.ports.system.Clock;

@AutoService(ChangeRecorder.class)
public final class ChangeHistory implements ChangeRecorder {

    static final int KEPT_IN_MEMORY = 5000;

    private final ChangeLogStore store;
    private final Clock clock;
    private final Deque<ChangeRecord> records = new ArrayDeque<>();
    private long lastId;
    private int keepDays = HistoryModule.DEFAULT_DAYS;

    public ChangeHistory(ChangeLogStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public synchronized void load(int days) {
        keepDays = days;
        records.clear();
        Instant from = cutoff();
        store.deleteBefore(from);
        for (ChangeRecord record : store.since(from)) {
            records.addFirst(record);
            lastId = Math.max(lastId, record.id());
            trim();
        }
    }

    public synchronized void keepDays(int days) {
        keepDays = days;
        Instant from = cutoff();
        records.removeIf(record -> record.at().isBefore(from));
        store.deleteBefore(from);
    }

    @Override
    public synchronized void record(Kind kind, String target, String oldValue, String newValue, boolean restart) {
        String before = oldValue == null ? "" : oldValue;
        String after = newValue == null ? "" : newValue;
        if (before.equals(after)) {
            return;
        }
        ChangeContext.Attribution by = ChangeContext.current();
        ChangeRecord record = new ChangeRecord(
                ++lastId, clock.now(), by.actor(), by.source(), kind, target, before, after, restart, by.undoes());
        records.addFirst(record);
        trim();
        store.append(record);
    }

    public synchronized List<ChangeRecord> newestFirst() {
        return List.copyOf(records);
    }

    public synchronized Set<String> targets() {
        Set<String> targets = new HashSet<>();
        records.forEach(record -> targets.add(record.target()));
        return targets;
    }

    public synchronized long newestId() {
        return records.isEmpty() ? 0 : records.getFirst().id();
    }

    public synchronized Optional<ChangeRecord> find(long id) {
        return records.stream().filter(record -> record.id() == id).findFirst();
    }

    public synchronized List<ChangeRecord> of(Kind kind, String target) {
        List<ChangeRecord> found = new ArrayList<>();
        for (ChangeRecord record : records) {
            if (record.kind() == kind && record.target().equals(target)) {
                found.add(record);
            }
        }
        return found;
    }

    public synchronized boolean rolledBack(long id) {
        return records.stream().anyMatch(record -> record.undoes() == id);
    }

    private Instant cutoff() {
        return clock.now().minus(Duration.ofDays(keepDays));
    }

    private void trim() {
        while (records.size() > KEPT_IN_MEMORY) {
            records.removeLast();
        }
    }
}
