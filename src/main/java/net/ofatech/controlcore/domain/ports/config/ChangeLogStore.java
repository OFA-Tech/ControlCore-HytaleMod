package net.ofatech.controlcore.domain.ports.config;

import java.time.Instant;
import java.util.List;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;

public interface ChangeLogStore {

    List<ChangeRecord> since(Instant from);

    void append(ChangeRecord record);

    void deleteBefore(Instant before);
}
