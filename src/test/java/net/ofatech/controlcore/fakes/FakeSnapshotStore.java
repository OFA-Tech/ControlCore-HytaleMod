package net.ofatech.controlcore.fakes;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.ports.config.SnapshotStore;
import net.ofatech.controlcore.domain.result.Result;

public final class FakeSnapshotStore implements SnapshotStore {

    public final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public List<Snapshot> all() {
        return snapshots.values().stream()
                .sorted(Comparator.comparing(Snapshot::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<Snapshot> find(String name) {
        return Optional.ofNullable(snapshots.get(name));
    }

    @Override
    public Result<Snapshot> save(Snapshot snapshot) {
        snapshots.put(snapshot.name(), snapshot);
        return Result.success(snapshot);
    }

    @Override
    public boolean delete(String name) {
        return snapshots.remove(name) != null;
    }
}
