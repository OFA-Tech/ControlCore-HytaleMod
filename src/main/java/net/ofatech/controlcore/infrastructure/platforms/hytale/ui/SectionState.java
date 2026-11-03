package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSearch;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;

final class SectionState {

    private Map<SettingId, Integer> indexes = new HashMap<>();
    private List<SettingsSection.Entry> entries = List.of();
    private SettingSearch search = SettingSearch.ALL;
    private SettingRows.State legend;
    private final Set<SettingId> inlineColors = new HashSet<>();
    private final Map<SettingId, String> shown = new HashMap<>();
    private final Map<SettingId, SettingSnapshot> latest = new HashMap<>();
    private final Map<SettingId, String> errors = new HashMap<>();
    private final Map<SettingId, String> unsaved = new HashMap<>();
    private final Map<SettingId, String> undo = new HashMap<>();
    private final Set<SettingId> undoRows = new HashSet<>();

    void startBuild() {
        indexes = new HashMap<>();
        inlineColors.clear();
    }

    void built(List<SettingsSection.Entry> entries) {
        this.entries = entries;
    }

    List<SettingsSection.Entry> entries() {
        return entries;
    }

    void index(SettingId id, int index) {
        indexes.put(id, index);
    }

    Integer index(SettingId id) {
        return indexes.get(id);
    }

    Map<SettingId, Integer> indexes() {
        return Collections.unmodifiableMap(indexes);
    }

    SettingSearch search() {
        return search;
    }

    void search(SettingSearch search) {
        this.search = search;
    }

    SettingRows.State legend() {
        return legend;
    }

    void legend(SettingRows.State legend) {
        this.legend = legend;
    }

    void inlineColor(SettingId id) {
        inlineColors.add(id);
    }

    boolean isInlineColor(SettingId id) {
        return inlineColors.contains(id);
    }

    void remember(SettingSnapshot snapshot) {
        shown.put(snapshot.id(), snapshot.value());
        latest.put(snapshot.id(), snapshot);
    }

    boolean shows(SettingId id, String value) {
        return value.equals(shown.get(id));
    }

    SettingSnapshot latest(SettingId id) {
        return latest.get(id);
    }

    SettingSnapshot latestOr(SettingSnapshot fallback) {
        return latest.getOrDefault(fallback.id(), fallback);
    }

    String typed(SettingId id) {
        return unsaved.get(id);
    }

    boolean isUnsaved(SettingId id) {
        return unsaved.containsKey(id);
    }

    boolean markTyped(SettingId id, String text, boolean same) {
        boolean wasUnsaved = unsaved.containsKey(id);
        if (same) {
            unsaved.remove(id);
        } else {
            unsaved.put(id, text);
        }
        return wasUnsaved == same;
    }

    void clearUnsaved(SettingId id) {
        unsaved.remove(id);
    }

    String error(SettingId id) {
        return errors.get(id);
    }

    void fail(SettingId id, String message) {
        errors.put(id, message);
    }

    void saved(SettingSnapshot before, SettingSnapshot after, boolean keepUndo) {
        errors.remove(after.id());
        unsaved.remove(after.id());
        if (keepUndo && before != null && !before.value().equals(after.value())) {
            undo.put(after.id(), before.value());
        }
        remember(after);
    }

    void listChanged(SettingSnapshot before, SettingSnapshot after) {
        undo.put(before.id(), before.value());
        errors.remove(before.id());
        remember(after);
    }

    String takeUndo(SettingId id) {
        return undo.remove(id);
    }

    void undone(SettingSnapshot snapshot) {
        remember(snapshot);
        errors.remove(snapshot.id());
        unsaved.remove(snapshot.id());
    }

    void undoFailed(SettingId id, String previous, String message) {
        undo.put(id, previous);
        errors.put(id, message);
    }

    void allowUndo(SettingId id) {
        undoRows.add(id);
    }

    boolean allowsUndo(SettingId id) {
        return undoRows.contains(id);
    }

    boolean hasUndo(SettingId id) {
        return undo.containsKey(id);
    }

    SettingRows.State state(SettingSnapshot snapshot, Predicate<SettingSnapshot> restartPending) {
        if (snapshot.access() == SettingAccess.READ_ONLY) {
            return SettingRows.State.LOCKED;
        }
        if (snapshot.access() == SettingAccess.APPLIES_AFTER_RESTART && restartPending.test(snapshot)) {
            return SettingRows.State.RESTART;
        }
        if (errors.containsKey(snapshot.id())) {
            return SettingRows.State.ERROR;
        }
        if (unsaved.containsKey(snapshot.id())) {
            return SettingRows.State.UNSAVED;
        }
        return snapshot.value().equals(snapshot.definition().formattedDefault())
                ? SettingRows.State.DEFAULT
                : SettingRows.State.CHANGED;
    }
}
