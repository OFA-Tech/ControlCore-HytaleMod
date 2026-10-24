package net.ofatech.controlcore.domain.model.snapshot;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.result.Result;

public interface SnapshotsView {

    record Entry(String name, String when, String who, String parts) {}

    record ModChoice(String id, String name) {}

    record Preview(String name, List<String> lines, int changes, int skipped) {}

    List<Entry> list();

    List<ModChoice> mods();

    Result<String> save(String name, Set<Snapshot.Part> parts, Optional<String> mod);

    Result<Preview> preview(String name);

    Result<String> restore(String name, boolean snapshotFirst);

    Result<String> delete(String name);
}
