package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.settings.PermissionsView;
import net.ofatech.controlcore.domain.model.settings.SettingSearch;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class PermissionsTab {

    static final PanelSection SECTION = PanelSection.PERMISSIONS;

    private static final String SAVE = "PermissionSave";
    private static final String RESET = "PermissionReset";
    private static final String ROW = "Pages/ControlCore/PermissionRow.ui";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);
    }

    private final PermissionsView view;
    private final Translator tr;
    private final Host host;
    private List<PermissionsView.Entry> entries = List.of();
    private final List<Integer> rowElements = new ArrayList<>();
    private final List<Integer> headingElements = new ArrayList<>();
    private int elementCount;
    private SettingSearch search = SettingSearch.ALL;

    PermissionsTab(PermissionsView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events, SettingSearch current) {
        search = current;
        entries = view.list();
        rowElements.clear();
        headingElements.clear();
        commands.set(SECTION.status + ".Text", "");
        String heading = null;
        int element = 0;
        for (PermissionsView.Entry entry : entries) {
            if (!entry.heading().equals(heading)) {
                heading = entry.heading();
                SettingRows.heading(SECTION, SECTION.row(element), heading, "", commands);
                element++;
            }
            headingElements.add(element - 1);
            rowElements.add(element);
            String row = SECTION.row(element);
            commands.append(SECTION.list, ROW);
            commands.set(row + " #View.PlaceholderText", tr.t("controlcore.panel.permissions.inherit"));
            commands.set(row + " #Edit.PlaceholderText", tr.t("controlcore.panel.permissions.inherit"));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Save",
                    SettingRows.event(SAVE, SECTION, entry.target())
                            .append(PanelAction.VALUE, row + " #View.Value")
                            .append(PanelAction.EDIT, row + " #Edit.Value"));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Reset",
                    SettingRows.event(RESET, SECTION, entry.target()));
            show(entry, row, commands);
            element++;
        }
        elementCount = element;
        applySearch(commands, false);
    }

    boolean handle(PanelAction action) {
        String name = action.name();
        if (!SAVE.equals(name) && !RESET.equals(name)) {
            return false;
        }
        Result<String> result =
                SAVE.equals(name) ? view.save(action.id, action.value, action.edit) : view.reset(action.id);
        if (result.isFailure()) {
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.error(result.error()));
            host.send(update);
            return true;
        }
        List<PermissionsView.Entry> fresh = view.list();
        if (!sameTargets(fresh)) {
            host.rebuild();
            return true;
        }
        entries = fresh;
        UICommandBuilder update = new UICommandBuilder();
        for (int index = 0; index < entries.size(); index++) {
            show(entries.get(index), SECTION.row(rowElements.get(index)), update);
        }
        applySearch(update, false);
        update.set("#FooterStatus.Text", result.value());
        host.send(update);
        return true;
    }

    void search(SettingSearch current, UICommandBuilder commands) {
        search = current;
        applySearch(commands, true);
    }

    private void show(PermissionsView.Entry entry, String row, UICommandBuilder commands) {
        commands.set(row + " #Label.Text", entry.name());
        commands.set(row + " #Description.Text", entry.description());
        commands.set(row + " #View.Value", entry.view());
        commands.set(row + " #Edit.Value", entry.edit());
        boolean ruled = !entry.view().isBlank() || !entry.edit().isBlank();
        commands.set(
                row + " #State.Background", (ruled ? SettingRows.State.CHANGED : SettingRows.State.DEFAULT).icon());
    }

    private void applySearch(UICommandBuilder commands, boolean report) {
        boolean[] visible = new boolean[entries.size()];
        for (int index = entries.size() - 1; index >= 0; index--) {
            PermissionsView.Entry entry = entries.get(index);
            visible[index] =
                    matches(entry) || (entry.level() != PermissionsView.Level.SETTING && anyChild(index, visible));
        }
        boolean[] headings = new boolean[elementCount];
        int rows = 0;
        int matching = 0;
        for (int index = 0; index < entries.size(); index++) {
            commands.set(SECTION.row(rowElements.get(index)) + ".Visible", visible[index]);
            if (visible[index]) {
                headings[headingElements.get(index)] = true;
            }
            if (entries.get(index).level() == PermissionsView.Level.SETTING) {
                rows++;
                matching += visible[index] ? 1 : 0;
            }
        }
        for (int element : headingElements) {
            commands.set(SECTION.row(element) + ".Visible", headings[element]);
        }
        if (report) {
            String status;
            if (search.isEmpty()) {
                status = "";
            } else if (matching == 0) {
                status = search.text().isEmpty()
                        ? tr.t("controlcore.panel.search.nothing_filtered")
                        : tr.t("controlcore.panel.search.nothing", "text", search.text());
            } else {
                status = tr.t("controlcore.panel.search.shown", "count", matching, "total", rows);
            }
            commands.set(SECTION.status + ".Text", status);
        }
    }

    private boolean matches(PermissionsView.Entry entry) {
        SettingSnapshot snapshot =
                new SettingSnapshot(entry.definition(), (entry.view() + " " + entry.edit()).strip(), entry.adminRule());
        return search.matches(snapshot, entry.name(), entry.heading(), entry.description());
    }

    private boolean anyChild(int group, boolean[] visible) {
        PermissionsView.Level level = entries.get(group).level();
        for (int index = group + 1; index < entries.size(); index++) {
            PermissionsView.Level child = entries.get(index).level();
            if (child == PermissionsView.Level.MOD
                    || (level == PermissionsView.Level.CATEGORY && child != PermissionsView.Level.SETTING)) {
                return false;
            }
            if (visible[index]) {
                return true;
            }
        }
        return false;
    }

    private boolean sameTargets(List<PermissionsView.Entry> fresh) {
        if (fresh.size() != entries.size()) {
            return false;
        }
        for (int index = 0; index < fresh.size(); index++) {
            if (!fresh.get(index).target().equals(entries.get(index).target())) {
                return false;
            }
        }
        return true;
    }
}
