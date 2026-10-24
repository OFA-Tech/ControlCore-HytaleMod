package net.ofatech.controlcore.domain.ports.config;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.result.Result;

public interface SnapshotStore {

    List<Snapshot> all();

    Optional<Snapshot> find(String name);

    Result<Snapshot> save(Snapshot snapshot);

    boolean delete(String name);
}
