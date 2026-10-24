package net.ofatech.controlcore.fakes;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.ports.config.ChangeLogStore;

public final class FakeChangeLogStore implements ChangeLogStore {

    public final List<ChangeRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public List<ChangeRecord> since(Instant from) {
        return records.stream().filter(record -> !record.at().isBefore(from)).toList();
    }

    @Override
    public void append(ChangeRecord record) {
        records.add(record);
    }

    @Override
    public void deleteBefore(Instant before) {
        records.removeIf(record -> record.at().isBefore(before));
    }
}
