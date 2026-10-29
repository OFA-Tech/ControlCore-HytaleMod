package net.ofatech.controlcore.domain.model.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

public interface SnapshotsView {

    record Entry(String name, String when, String who, String parts, boolean profile) {}

    record ModChoice(String id, String name) {}

    record Preview(String name, List<String> lines, int changes, int skipped, int adminOnly) {

        public String title(Translator tr) {
            String key = changes > 0
                    ? "controlcore.snapshots.preview.header"
                    : adminOnly > 0 ? "controlcore.snapshots.preview.only_admin" : "controlcore.snapshots.preview.same";
            return tr.t(key, "name", name, "count", changes);
        }

        public List<String> allLines(Translator tr) {
            List<String> all = new ArrayList<>(lines);
            if (adminOnly > 0) {
                all.add(tr.t("controlcore.snapshots.preview.admin_only", "count", adminOnly));
            }
            if (skipped > 0) {
                all.add(tr.t("controlcore.snapshots.preview.skipped", "count", skipped));
            }
            return all;
        }
    }

    List<Entry> list();

    List<ModChoice> mods();

    Result<String> save(String name, Set<Snapshot.Part> parts, Optional<String> mod);

    Result<Preview> preview(String name);

    Result<String> restore(String name, boolean snapshotFirst);

    Result<String> delete(String name);

    Result<String> setProfile(String name, boolean profile);

    Result<String> deleteAll();

    Result<String> saveBefore(String base, Set<Snapshot.Part> parts);
}
