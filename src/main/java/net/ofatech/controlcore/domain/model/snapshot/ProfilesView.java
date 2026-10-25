package net.ofatech.controlcore.domain.model.snapshot;

import java.util.List;
import net.ofatech.controlcore.domain.result.Result;

public interface ProfilesView {

    record Entry(String id, String name, String details, boolean builtIn, boolean active) {}

    List<Entry> list();

    String status();

    Result<SnapshotsView.Preview> preview(String id);

    Result<String> switchTo(String id);

    Result<String> unmark(String id);
}
