package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part;
import net.ofatech.controlcore.domain.model.snapshot.SnapshotsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class SnapshotsTab {

    static final int LIST_HEIGHT = 440;
    static final int PREVIEW_HEIGHT = 224;

    private static final String PART = "SnapshotPart";
    private static final String SAVE = "SnapshotSave";
    private static final String PREVIEW = "SnapshotPreview";
    private static final String DELETE = "SnapshotDelete";
    private static final String RESTORE = "SnapshotRestore";
    private static final String RESTORE_SAFE = "SnapshotRestoreSafe";
    private static final String CLOSE_PREVIEW = "SnapshotPreviewClose";
    private static final String PROFILE = "SnapshotProfile";
    private static final String DELETE_ALL = "SnapshotDeleteAll";
    private static final String ALL_MODS = "*";
    private static final String ROW = "Pages/ControlCore/SnapshotRow.ui";
    private static final String LIST = "#SnapshotList";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void showRestartNotice(UICommandBuilder update);

        void resize(String element, int height, UICommandBuilder update);
    }

    private final SnapshotsView view;
    private final Translator tr;
    private final Host host;
    private final Set<Part> parts =
            EnumSet.of(Part.CONFIG, Part.WORLDS, Part.SETTINGS, Part.KEYS, Part.PERMISSIONS, Part.MODS);
    private String name = "";
    private String mod = ALL_MODS;
    private Optional<String> previewing = Optional.empty();
    private Optional<String> armedDelete = Optional.empty();
    private boolean armedDeleteAll;

    SnapshotsTab(SnapshotsView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
        if (!view.admin()) {
            parts.removeIf(Part::adminOnly);
        }
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#SnapshotName.Value", name);
        commands.set("#SnapshotName.PlaceholderText", tr.t("controlcore.panel.snapshots.name_hint"));
        List<DropdownEntryInfo> mods = new ArrayList<>();
        mods.add(entry(tr.t("controlcore.panel.snapshots.all_mods"), ALL_MODS));
        for (SnapshotsView.ModChoice choice : view.mods()) {
            mods.add(entry(tr.t("controlcore.panel.snapshots.only_mod", "name", choice.name()), choice.id()));
        }
        commands.set("#SnapshotMods.Entries", mods);
        commands.set("#SnapshotMods.Value", mod);
        for (Part part : Part.values()) {
            String box = "#SnapPart" + label(part);
            commands.set(box + ".Value", parts.contains(part));
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    box,
                    new EventData()
                            .append(PanelAction.KEY, PART)
                            .append(PanelAction.ID, part.id())
                            .append(PanelAction.CHECKED, box + ".Value"));
        }
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SnapshotSave",
                new EventData()
                        .append(PanelAction.KEY, SAVE)
                        .append(PanelAction.VALUE, "#SnapshotName.Value")
                        .append(PanelAction.FILTER, "#SnapshotMods.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#SnapshotRestore", EventData.of(PanelAction.KEY, RESTORE));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SnapshotRestoreSafe",
                EventData.of(PanelAction.KEY, RESTORE_SAFE));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SnapshotPreviewClose",
                EventData.of(PanelAction.KEY, CLOSE_PREVIEW));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#SnapshotsDeleteAll", EventData.of(PanelAction.KEY, DELETE_ALL));
        armedDeleteAll = false;
        List<SnapshotsView.Entry> entries = view.list();
        commands.set("#SnapshotsEmpty.Visible", entries.isEmpty());
        for (int index = 0; index < entries.size(); index++) {
            SnapshotsView.Entry entry = entries.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Name.Text", entry.name());
            commands.set(
                    row + " #Details.Text",
                    tr.t(
                            "controlcore.panel.snapshots.details",
                            "when",
                            entry.when(),
                            "who",
                            entry.who().isEmpty() ? "-" : entry.who(),
                            "parts",
                            entry.parts()));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Preview",
                    new EventData().append(PanelAction.KEY, PREVIEW).append(PanelAction.ID, entry.name()));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Delete",
                    new EventData().append(PanelAction.KEY, DELETE).append(PanelAction.ID, entry.name()));
            commands.set(
                    row + " #Profile.Text",
                    tr.t(
                            entry.profile()
                                    ? "controlcore.panel.snapshots.is_profile"
                                    : "controlcore.panel.snapshots.make_profile"));
            commands.set(row + " #Delete.Visible", !entry.profile() && !entry.locked());
            commands.set(row + " #Profile.Visible", view.admin() || (!entry.locked() && !entry.profile()));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Profile",
                    new EventData()
                            .append(PanelAction.KEY, PROFILE)
                            .append(PanelAction.ID, entry.name())
                            .append(PanelAction.ITEM, Boolean.toString(!entry.profile())));
        }
        Optional<String> open = previewing;
        previewing = Optional.empty();
        open.ifPresentOrElse(snapshot -> showPreview(snapshot, commands), () -> hidePreview(commands));
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case PART ->
                Part.of(action.id).ifPresent(part -> {
                    if (Boolean.TRUE.equals(action.checked)) {
                        parts.add(part);
                    } else {
                        parts.remove(part);
                    }
                    host.send(new UICommandBuilder());
                });
            case SAVE -> save(action);
            case PREVIEW -> {
                UICommandBuilder update = new UICommandBuilder();
                armedDelete = Optional.empty();
                showPreview(action.id, update);
                host.send(update);
            }
            case CLOSE_PREVIEW -> {
                UICommandBuilder update = new UICommandBuilder();
                previewing = Optional.empty();
                hidePreview(update);
                host.send(update);
            }
            case RESTORE, RESTORE_SAFE -> restore(RESTORE_SAFE.equals(action.name()));
            case DELETE -> delete(action.id);
            case PROFILE -> {
                if (action.id == null) {
                    host.send(new UICommandBuilder());
                } else {
                    Result<String> result = view.setProfile(action.id, Boolean.parseBoolean(action.item));
                    if (result.isSuccess()) {
                        host.rebuild();
                    }
                    footer(result);
                }
            }
            case DELETE_ALL -> deleteAll();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void save(PanelAction action) {
        name = action.value == null ? "" : action.value.strip();
        mod = action.filter == null || action.filter.isBlank() ? ALL_MODS : action.filter;
        Optional<String> onlyMod = ALL_MODS.equals(mod) ? Optional.empty() : Optional.of(mod);
        Result<String> result = view.save(name, parts, onlyMod);
        if (result.isSuccess()) {
            name = "";
            host.rebuild();
        }
        footer(result);
    }

    private void restore(boolean saveFirst) {
        if (previewing.isEmpty()) {
            host.send(new UICommandBuilder());
            return;
        }
        Result<String> result = view.restore(previewing.get(), saveFirst);
        if (result.isSuccess()) {
            previewing = Optional.empty();
            host.rebuild();
        }
        footer(result);
    }

    private void deleteAll() {
        if (!armedDeleteAll) {
            armedDeleteAll = true;
            UICommandBuilder update = new UICommandBuilder();
            int kept = view.keptForAdmins();
            update.set(
                    "#FooterStatus.Text",
                    tr.t("controlcore.panel.snapshots.confirm_delete_all")
                            + (kept == 0 ? "" : " " + tr.t("controlcore.panel.snapshots.will_keep", "count", kept)));
            update.set("#SnapshotsDeleteAll.Text", tr.t("controlcore.panel.confirm"));
            host.send(update);
            return;
        }
        armedDeleteAll = false;
        Result<String> result = view.deleteAll();
        if (result.isSuccess()) {
            previewing = Optional.empty();
            host.rebuild();
        }
        footer(result);
    }

    private void delete(String snapshot) {
        if (snapshot == null) {
            host.send(new UICommandBuilder());
            return;
        }
        if (armedDelete.filter(snapshot::equals).isEmpty()) {
            armedDelete = Optional.of(snapshot);
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t("controlcore.panel.snapshots.confirm_delete", "name", snapshot));
            host.send(update);
            return;
        }
        armedDelete = Optional.empty();
        Result<String> result = view.delete(snapshot);
        if (result.isSuccess()) {
            previewing = previewing.filter(open -> !open.equals(snapshot));
            host.rebuild();
        }
        footer(result);
    }

    private void showPreview(String snapshot, UICommandBuilder update) {
        Result<SnapshotsView.Preview> result = view.preview(snapshot);
        if (result.isFailure()) {
            update.set("#FooterStatus.Text", tr.error(result.error()));
            hidePreview(update);
            return;
        }
        SnapshotsView.Preview preview = result.value();
        previewing = Optional.of(preview.name());
        List<String> lines = preview.allLines(tr);
        update.set("#SnapshotPreviewTitle.Text", preview.title(tr));
        update.set("#SnapshotPreviewText.Text", String.join("\n", lines));
        update.set("#SnapshotRestore.Visible", preview.changes() > 0);
        update.set("#SnapshotRestoreSafe.Visible", preview.changes() > 0);
        update.set("#SnapshotPreview.Visible", true);
        update.set("#SnapshotForm.Visible", false);
        host.resize(LIST, LIST_HEIGHT - PREVIEW_HEIGHT + 112, update);
    }

    private void hidePreview(UICommandBuilder update) {
        update.set("#SnapshotPreview.Visible", false);
        update.set("#SnapshotForm.Visible", true);
        host.resize(LIST, LIST_HEIGHT, update);
    }

    private void footer(Result<String> result) {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.showRestartNotice(update);
        host.send(update);
    }

    private static DropdownEntryInfo entry(String text, String value) {
        return new DropdownEntryInfo(LocalizableString.fromString(text), value);
    }

    private static String label(Part part) {
        String id = part.id();
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
