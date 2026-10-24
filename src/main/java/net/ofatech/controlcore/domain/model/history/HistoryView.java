package net.ofatech.controlcore.domain.model.history;

import java.util.List;
import net.ofatech.controlcore.domain.result.Result;

public interface HistoryView {

    record Entry(
            long id,
            ChangeRecord.Kind kind,
            String target,
            String when,
            String who,
            String source,
            String what,
            String before,
            String after,
            boolean restart,
            boolean rollback,
            boolean rolledBack,
            boolean canRollback) {}

    List<Entry> entries();

    long newest();

    boolean canRollback();

    Result<String> rollback(long id);
}
