package net.ofatech.controlcore.application.history;

import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;

public interface ChangeRecorder {

    ChangeRecorder NONE = (kind, target, oldValue, newValue, restart) -> {};

    void record(Kind kind, String target, String oldValue, String newValue, boolean restart);
}
