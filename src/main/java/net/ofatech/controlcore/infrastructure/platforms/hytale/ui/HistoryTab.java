package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class HistoryTab {

    static final int ROWS = 200;

    private static final String SEARCH = "HistorySearch";
    private static final String ROLLBACK = "HistoryRollback";
    private static final String CLEAR = "HistoryClear";
    private static final List<String> AGES = List.of("7", "30", "90", "all");
    private static final String ROW = "Pages/ControlCore/HistoryRow.ui";
    private static final String LIST = "#HistoryList";
    private static final String ICONS = "Pages/ControlCore/Icons/";

    enum Filter {
        ALL(null),
        SETTINGS(ChangeRecord.Kind.SETTING),
        CONFIG(ChangeRecord.Kind.SERVER_CONFIG),
        WORLDS(ChangeRecord.Kind.SERVER_CONFIG),
        KEYS(ChangeRecord.Kind.SERVER_KEYS),
        BINDS(ChangeRecord.Kind.CUSTOM_BIND),
        PERMISSIONS(ChangeRecord.Kind.PERMISSION),
        MODS(ChangeRecord.Kind.MOD),
        BANS(ChangeRecord.Kind.BAN);

        private final ChangeRecord.Kind kind;

        Filter(ChangeRecord.Kind kind) {
            this.kind = kind;
        }

        static Filter of(String name) {
            return Arrays.stream(values())
                    .filter(filter -> filter.name().equals(name))
                    .findFirst()
                    .orElse(ALL);
        }

        boolean accepts(HistoryView.Entry entry) {
            if (kind == null || entry.kind() != kind) {
                return kind == null;
            }
            boolean world = entry.target().startsWith("world:");
            return this == WORLDS ? world : this != CONFIG || !world;
        }
    }

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void showRestartNotice(UICommandBuilder update);
    }

    private final HistoryView view;
    private final Translator tr;
    private final Host host;
    private List<HistoryView.Entry> entries = List.of();
    private int total;
    private long newest;
    private String query = "";
    private Filter filter = Filter.ALL;
    private Set<String> targets;
    private String clearAge = "90";
    private String armedClear;

    HistoryTab(HistoryView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        List<HistoryView.Entry> all = view.entries();
        targets = null;
        total = all.size();
        newest = all.isEmpty() ? 0 : all.getFirst().id();
        entries = all.subList(0, Math.min(ROWS, all.size()));
        commands.set(
                "#HistoryFilter.Entries",
                Arrays.stream(Filter.values())
                        .map(option -> new DropdownEntryInfo(
                                LocalizableString.fromString(tr.t("controlcore.panel.history.filter."
                                        + option.name().toLowerCase(Locale.ROOT))),
                                option.name()))
                        .toList());
        commands.set("#HistoryFilter.Value", filter.name());
        commands.set("#HistorySearch.Value", query);
        commands.set("#HistorySearch.PlaceholderText", tr.t("controlcore.panel.history.search_hint"));
        EventData search = new EventData()
                .append(PanelAction.KEY, SEARCH)
                .append(PanelAction.VALUE, "#HistorySearch.Value")
                .append(PanelAction.FILTER, "#HistoryFilter.Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HistorySearch", search);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HistoryFilter", search);
        commands.set("#HistoryClearAge.Visible", view.canClear());
        commands.set("#HistoryClear.Visible", view.canClear());
        if (view.canClear()) {
            commands.set(
                    "#HistoryClearAge.Entries",
                    AGES.stream()
                            .map(age -> new DropdownEntryInfo(
                                    LocalizableString.fromString(tr.t("controlcore.panel.history.clear_age." + age)),
                                    age))
                            .toList());
            commands.set("#HistoryClearAge.Value", clearAge);
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#HistoryClear",
                    new EventData().append(PanelAction.KEY, CLEAR).append(PanelAction.VALUE, "#HistoryClearAge.Value"));
        }
        commands.set("#HistoryEmpty.Visible", entries.isEmpty());
        for (int index = 0; index < entries.size(); index++) {
            HistoryView.Entry entry = entries.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #State.Background", ICONS + icon(entry));
            commands.set(row + " #What.Text", entry.what());
            commands.set(
                    row + " #Change.Text",
                    tr.t("controlcore.panel.history.change", "before", entry.before(), "after", entry.after()));
            commands.set(row + " #Details.Text", details(entry));
            commands.set(row + " #RolledBack.Visible", entry.rolledBack());
            commands.set(row + " #RolledBack.Text", tr.t("controlcore.panel.history.rolled_back"));
            commands.set(row + " #Rollback.Visible", entry.canRollback());
            if (entry.canRollback()) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " #Rollback",
                        new EventData()
                                .append(PanelAction.KEY, ROLLBACK)
                                .append(PanelAction.ID, Long.toString(entry.id())));
            }
        }
        applySearch(commands);
    }

    boolean has(String target) {
        if (targets == null) {
            targets = view.targets();
        }
        return targets.contains(target) || targets.contains("setting:" + target);
    }

    void focus(String target, UICommandBuilder update) {
        query = target;
        filter = Filter.ALL;
        update.set("#HistorySearch.Value", query);
        update.set("#HistoryFilter.Value", filter.name());
        applySearch(update);
    }

    boolean stale() {
        return view.newest() != newest;
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case SEARCH -> {
                query = action.value == null ? "" : action.value;
                filter = Filter.of(action.filter);
                UICommandBuilder update = new UICommandBuilder();
                applySearch(update);
                host.send(update);
            }
            case ROLLBACK -> rollback(action.id);
            case CLEAR -> clear(action.value);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void clear(String age) {
        String chosen = age != null && AGES.contains(age) ? age : clearAge;
        UICommandBuilder update = new UICommandBuilder();
        if (!chosen.equals(armedClear)) {
            armedClear = chosen;
            clearAge = chosen;
            update.set(
                    "#FooterStatus.Text",
                    tr.t(
                            "controlcore.panel.history.confirm_clear",
                            "what",
                            tr.t("controlcore.panel.history.clear_age." + chosen)));
            update.set("#HistoryClear.Text", tr.t("controlcore.panel.confirm"));
            host.send(update);
            return;
        }
        armedClear = null;
        Optional<Duration> olderThan =
                chosen.equals("all") ? Optional.empty() : Optional.of(Duration.ofDays(Integer.parseInt(chosen)));
        Result<String> result = view.clear(olderThan);
        if (result.isSuccess()) {
            host.rebuild();
        }
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.send(update);
    }

    private void rollback(String id) {
        Result<String> result;
        try {
            result = view.rollback(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return;
        }
        if (result.isSuccess()) {
            host.rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.showRestartNotice(update);
        host.send(update);
    }

    private void applySearch(UICommandBuilder commands) {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int index = 0; index < entries.size(); index++) {
            HistoryView.Entry entry = entries.get(index);
            boolean visible = filter.accepts(entry) && matches(entry, wanted);
            commands.set(LIST + "[" + index + "].Visible", visible);
            if (visible) {
                shown++;
            }
        }
        commands.set(
                "#HistoryStatus.Text",
                total > entries.size()
                        ? tr.t(
                                "controlcore.panel.history.shown_newest",
                                "shown",
                                shown,
                                "newest",
                                entries.size(),
                                "total",
                                total)
                        : tr.t("controlcore.panel.history.shown", "shown", shown, "total", total));
    }

    private static boolean matches(HistoryView.Entry entry, String wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        return String.join(
                        "\n",
                        entry.what(),
                        entry.who(),
                        entry.target(),
                        entry.before(),
                        entry.after(),
                        entry.source(),
                        "#" + entry.id())
                .toLowerCase(Locale.ROOT)
                .contains(wanted);
    }

    private String details(HistoryView.Entry entry) {
        String text = tr.t(
                "controlcore.panel.history.details",
                "id",
                entry.id(),
                "when",
                entry.when(),
                "who",
                entry.who(),
                "source",
                entry.source());
        return entry.restart() ? text + " · " + tr.t("controlcore.panel.history.restart") : text;
    }

    private static String icon(HistoryView.Entry entry) {
        if (entry.rollback() || entry.rolledBack()) {
            return "Undo.png";
        }
        return entry.restart() ? "Restart.png" : "Changed.png";
    }
}
