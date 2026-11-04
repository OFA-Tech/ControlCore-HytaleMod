package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.settings.DefaultsView;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

final class SettingRowEditor {

    static final String RESTORE_DEFAULTS = "RestoreDefaults";
    static final String HISTORY_LINK = "HistoryOf";

    interface Host {
        PlayerRef viewer();

        void restartBanner(UICommandBuilder update, boolean pending);

        void rebuild();

        void send(UICommandBuilder update);

        void stopListening();

        boolean isOpen(Ref<EntityStore> ref, Store<EntityStore> store);

        void showLegend(UICommandBuilder update);

        void worldChanged(SettingId changed);

        boolean hasHistory(SettingId id);

        boolean customBind(PanelSection key, SettingId id);

        Optional<GameActionMode> gameAction(PanelSection key, SettingId id);

        String conflictNote(PanelSection key, SettingsSection section, SettingSnapshot saved);

        Set<String> shownIds(PanelSection key);

        Optional<Set<String>> permissionTargets();
    }

    private final Map<PanelSection, SettingsSection> sections;
    private final Map<PanelSection, SectionState> states;
    private final Supplier<List<Text>> pendingRestart;
    private final DefaultsView defaults;
    private final Translator tr;
    private final Host host;
    private final Log log;
    private Set<String> restartNames = Set.of();
    private Optional<PanelSection> armedDefaults = Optional.empty();

    SettingRowEditor(
            Map<PanelSection, SettingsSection> sections,
            Map<PanelSection, SectionState> states,
            Supplier<List<Text>> pendingRestart,
            DefaultsView defaults,
            Translator tr,
            Host host,
            Log log) {
        this.sections = sections;
        this.states = states;
        this.pendingRestart = pendingRestart;
        this.defaults = defaults;
        this.tr = tr;
        this.host = host;
        this.log = log;
    }

    static void footer(UICommandBuilder update, String text) {
        update.set("#FooterStatus.Text", text);
    }

    void disarmDefaults() {
        armedDefaults = Optional.empty();
    }

    void build(PanelSection key, SettingsSection section, UICommandBuilder commands, UIEventBuilder events) {
        SectionState state = states.get(key);
        state.startBuild();
        commands.set(key.status + ".Text", "");
        List<SettingsSection.Entry> entries = section.entries();
        state.built(entries);
        for (int index = 0; index < entries.size(); index++) {
            String element = key.row(index);
            switch (entries.get(index)) {
                case SettingsSection.Heading heading ->
                    SettingRows.heading(key, element, heading.text(), headingCount(entries, index), commands);
                case SettingsSection.Row row -> {
                    state.index(row.id(), index);
                    state.remember(row.snapshot());
                    int rowIndex = index;
                    row.color().ifPresent(color -> {
                        state.index(color.id(), rowIndex);
                        state.remember(color);
                        state.inlineColor(color.id());
                    });
                    boolean customBind = host.customBind(key, row.id());
                    Optional<GameActionMode> gameAction = host.gameAction(key, row.id());
                    SettingRows.build(key, section, row, element, customBind, gameAction, commands, events);
                    String typed = state.typed(row.id());
                    if (typed != null && SettingRows.tracksTyping(row.kind()) && !sliderCanFollow(row.kind(), typed)) {
                        commands.set(element + " #Input.Value", typed);
                    } else if (typed != null && SettingRows.tracksTyping(row.kind())) {
                        SettingSnapshot shown = row.snapshot();
                        SettingRows.showValue(
                                section,
                                row.kind(),
                                element,
                                new SettingSnapshot(shown.definition(), typed, shown.overridden(), shown.access()),
                                commands);
                    }
                    showHistoryLink(key, element, row.id(), commands, events);
                    if (SettingRows.canUndo(row)) {
                        state.allowUndo(row.id());
                    }
                    showState(key, index, state.latestOr(row.snapshot()), commands);
                }
            }
        }
    }

    private void showHistoryLink(
            PanelSection key, String element, SettingId id, UICommandBuilder commands, UIEventBuilder events) {
        boolean serverWide =
                key == PanelSection.SERVER_CONFIG || key == PanelSection.SETTINGS || key == PanelSection.SERVER_KEYMAPS;
        if (!serverWide || !host.hasHistory(id)) {
            return;
        }
        commands.set(element + " #History.Visible", true);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                element + " #History",
                new EventData().append(PanelAction.KEY, HISTORY_LINK).append(PanelAction.ID, id.toString()));
    }

    private String headingCount(List<SettingsSection.Entry> entries, int heading) {
        int count = 0;
        for (int index = heading + 1; index < entries.size(); index++) {
            if (entries.get(index) instanceof SettingsSection.Heading) {
                break;
            }
            count++;
        }
        return count == 1
                ? tr.t("controlcore.panel.heading_count_one")
                : tr.t("controlcore.panel.heading_count", "count", count);
    }

    SettingRows.State state(PanelSection key, SettingSnapshot snapshot) {
        return states.get(key)
                .state(
                        snapshot,
                        pending -> restartNames.contains(sections.get(key).name(pending)));
    }

    private void showState(PanelSection key, int index, SettingSnapshot snapshot, UICommandBuilder update) {
        SectionState state = states.get(key);
        String row = key.row(index);
        String error = state.error(snapshot.id());
        update.set(row + " #State.Background", state(key, snapshot).icon());
        update.set(row + " #Error.Visible", error != null);
        update.set(row + " #Error.Text", error == null ? "" : error);
        if (state.allowsUndo(snapshot.id())) {
            update.set(row + " #Undo.Visible", state.hasUndo(snapshot.id()));
        }
    }

    void handle(PanelSection key, SettingsSection section, String name, PanelAction action) {
        switch (name) {
            case SettingRows.SLIDE -> {
                showSliderText(key, action);
                return;
            }
            case SettingRows.PICK -> {
                showPickedColor(key, action, true);
                return;
            }
            case SettingRows.PICK_INLINE -> {
                showPickedColor(key, action, false);
                return;
            }
            case SettingRows.SAVE_COLORED, SettingRows.RESET_COLORED -> {
                saveColored(key, section, SettingRows.RESET_COLORED.equals(name), action);
                return;
            }
            case SettingRows.ADD_ITEM, SettingRows.REMOVE_ITEM -> {
                changeList(key, section, name, action);
                return;
            }
            case SettingRows.TYPED -> {
                typed(key, action);
                return;
            }
            case SettingRows.UNDO -> {
                undoChange(key, section, action);
                return;
            }
            case SettingRows.PICK_KEYS -> {
                SettingSnapshot current = latestOf(key, action.id);
                if (current == null) {
                    return;
                }
                action.value = KeyPicker.compose(action.value, action.bindKeys, current.value());
                if (action.value.equals(current.value())) {
                    UICommandBuilder update = new UICommandBuilder();
                    Integer index = states.get(key).index(current.id());
                    if (index != null) {
                        showRowValue(key, section, index, current, update);
                    }
                    host.send(update);
                    return;
                }
                name = SettingRows.SAVE;
            }
            default -> {}
        }
        Supplier<Result<SettingSnapshot>> change = switch (name) {
            case SettingRows.SAVE, SettingRows.CHOOSE -> () -> section.save(action.id, action.value);
            case SettingRows.SLID ->
                sliderValue(key, action)
                        .<Supplier<Result<SettingSnapshot>>>map(value -> () -> section.save(action.id, value))
                        .orElse(null);
            case SettingRows.CHECK -> () -> section.check(action.id, action.checked);
            case SettingRows.RESET -> () -> section.reset(action.id);
            default -> null;
        };
        if (change == null) {
            return;
        }
        boolean controlAlreadyShowsValue = SettingRows.CHECK.equals(name) || SettingRows.CHOOSE.equals(name);
        UICommandBuilder update = new UICommandBuilder();
        Result<SettingSnapshot> saved = saveValue(key, section, action.id, change, controlAlreadyShowsValue, update);
        if (saved.isSuccess()) {
            String note = section.kind(saved.value()) == SettingsSection.Kind.KEYS
                    ? host.conflictNote(key, section, saved.value())
                    : "";
            footer(update, section.saved(saved.value()) + note);
        }
        host.send(update);
    }

    private void typed(PanelSection key, PanelAction action) {
        SettingSnapshot snapshot = latestOf(key, action.id);
        Integer index = indexOf(key, action.id);
        if (snapshot == null || index == null) {
            return;
        }
        String text = action.value == null ? "" : action.value;
        UICommandBuilder update = new UICommandBuilder();
        if (markTyped(
                key,
                index,
                snapshot,
                text,
                sameShown(sections.get(key).kind(snapshot), snapshot.value(), text),
                update)) {
            host.send(update);
        }
    }

    private void undoChange(PanelSection key, SettingsSection section, PanelAction action) {
        SettingSnapshot current = latestOf(key, action.id);
        if (current == null) {
            return;
        }
        SectionState state = states.get(key);
        String previous = state.takeUndo(current.id());
        if (previous == null) {
            return;
        }
        Result<SettingSnapshot> result = section.save(action.id, previous);
        UICommandBuilder update = new UICommandBuilder();
        if (key == PanelSection.SERVER_CONFIG) {
            showRestartNotice(update);
        }
        Integer index = indexOf(key, action.id);
        if (result.isSuccess()) {
            SettingSnapshot snapshot = result.value();
            state.undone(snapshot);
            footer(
                    update,
                    tr.t(
                            "controlcore.panel.undone",
                            "name",
                            section.name(snapshot),
                            "value",
                            section.shownValue(snapshot)));
            if (SettingsSection.kindOf(snapshot) == SettingsSection.Kind.LIST) {
                host.rebuild();
            } else if (index != null) {
                showRowValue(key, section, index, snapshot, update);
                showState(key, index, snapshot, update);
            }
        } else {
            state.undoFailed(current.id(), previous, tr.error(result.error()));
            footer(update, tr.error(result.error()));
            if (index != null) {
                showState(key, index, current, update);
            }
        }
        host.showLegend(update);
        host.send(update);
    }

    private void failed(PanelSection key, String idText, String message, UICommandBuilder update) {
        footer(update, message);
        SettingSnapshot snapshot = latestOf(key, idText);
        Integer index = indexOf(key, idText);
        if (snapshot != null && index != null) {
            states.get(key).fail(snapshot.id(), message);
            showState(key, index, snapshot, update);
        }
        host.showLegend(update);
    }

    private void succeeded(
            PanelSection key,
            SettingSnapshot before,
            SettingSnapshot after,
            boolean keepUndo,
            UICommandBuilder update) {
        SectionState state = states.get(key);
        state.saved(before, after, keepUndo);
        Integer index = state.index(after.id());
        if (index != null) {
            showState(key, index, after, update);
        }
        host.showLegend(update);
        if (key == PanelSection.SERVER_CONFIG) {
            host.worldChanged(after.id());
        }
    }

    Result<SettingSnapshot> saveValue(
            PanelSection key,
            SettingsSection section,
            String id,
            Supplier<Result<SettingSnapshot>> change,
            boolean controlAlreadyShowsValue,
            UICommandBuilder update) {
        SettingSnapshot before = latestOf(key, id);
        Result<SettingSnapshot> result = change.get();
        if (key == PanelSection.SERVER_CONFIG) {
            showRestartNotice(update);
        }
        result.onSuccess(snapshot -> {
                    Integer index = states.get(key).index(snapshot.id());
                    if (!controlAlreadyShowsValue && index != null) {
                        showRowValue(key, section, index, snapshot, update);
                    }
                    succeeded(key, before, snapshot, true, update);
                })
                .onFailure(error -> failed(key, id, tr.error(error), update));
        return result;
    }

    private void saveColored(PanelSection key, SettingsSection section, boolean reset, PanelAction action) {
        Result<SettingSnapshot> text = reset ? section.reset(action.id) : section.save(action.id, action.value);
        Result<SettingSnapshot> color = null;
        if (text.isSuccess() && action.item != null) {
            if (reset) {
                color = section.reset(action.item);
            } else {
                String hex = SettingRows.pickedHex(action.color);
                if (hex != null) {
                    color = section.save(action.item, hex);
                }
            }
        }
        SectionState state = states.get(key);
        UICommandBuilder update = new UICommandBuilder();
        if (text.isSuccess()) {
            SettingSnapshot saved = text.value();
            footer(update, section.saved(saved));
            Integer index = state.index(saved.id());
            if (index != null) {
                SettingRows.showValue(section, SettingsSection.Kind.TEXT, key.row(index), saved, update);
            }
            succeeded(key, null, saved, false, update);
        } else {
            failed(key, action.id, tr.error(text.error()), update);
        }
        if (color != null && color.isSuccess()) {
            SettingSnapshot saved = color.value();
            state.remember(saved);
            Integer index = state.index(saved.id());
            if (index != null) {
                SettingRows.showPicker(key.row(index), saved, update);
            }
        } else if (color != null) {
            footer(update, tr.error(color.error()));
        }
        host.send(update);
    }

    private void changeList(PanelSection key, SettingsSection section, String name, PanelAction action) {
        SettingSnapshot snapshot = latestOf(key, action.id);
        if (snapshot == null) {
            return;
        }
        Result<SettingSnapshot> result;
        if (SettingRows.ADD_ITEM.equals(name)) {
            result = section.addItem(snapshot, action.value);
        } else {
            int index;
            try {
                index = Integer.parseInt(action.item == null ? "" : action.item);
            } catch (NumberFormatException e) {
                return;
            }
            result = section.removeItem(snapshot, index);
        }
        UICommandBuilder update = new UICommandBuilder();
        if (result.isSuccess()) {
            states.get(key).listChanged(snapshot, result.value());
            host.rebuild();
            footer(update, section.saved(result.value()));
        } else {
            failed(key, action.id, tr.error(result.error()), update);
        }
        host.send(update);
    }

    private void showPickedColor(PanelSection key, PanelAction action, boolean fillField) {
        String hex = SettingRows.pickedHex(action.value);
        Integer index = indexOf(key, action.id);
        if (hex == null || index == null) {
            return;
        }
        UICommandBuilder update = new UICommandBuilder();
        if (fillField) {
            update.set(key.row(index) + " #Input.Value", hex);
            SettingSnapshot snapshot = latestOf(key, action.id);
            if (snapshot != null) {
                markTyped(
                        key,
                        index,
                        snapshot,
                        hex,
                        sameShown(sections.get(key).kind(snapshot), snapshot.value(), hex),
                        update);
            }
        }
        footer(update, tr.t("controlcore.panel.color_picked", "color", hex));
        host.send(update);
    }

    private void showSliderText(PanelSection key, PanelAction action) {
        Optional<String> value = sliderValue(key, action);
        Integer index = indexOf(key, action.id);
        if (value.isEmpty() || index == null) {
            return;
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(key.row(index) + " #Input.Value", value.get());
        SettingSnapshot snapshot = latestOf(key, action.id);
        if (snapshot != null) {
            markTyped(
                    key,
                    index,
                    snapshot,
                    value.get(),
                    sameShown(sections.get(key).kind(snapshot), snapshot.value(), value.get()),
                    update);
        }
        host.send(update);
    }

    private boolean markTyped(
            PanelSection key, int index, SettingSnapshot snapshot, String text, boolean same, UICommandBuilder update) {
        if (!states.get(key).markTyped(snapshot.id(), text, same)) {
            return false;
        }
        showState(key, index, snapshot, update);
        host.showLegend(update);
        return true;
    }

    private static boolean sliderCanFollow(SettingsSection.Kind kind, String text) {
        if (kind != SettingsSection.Kind.INTEGER && kind != SettingsSection.Kind.DECIMAL) {
            return true;
        }
        try {
            new BigDecimal(text.strip());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean sameShown(SettingsSection.Kind kind, String saved, String shown) {
        return switch (kind) {
            case INTEGER -> sameWhole(saved, shown);
            case DECIMAL -> sameNumber(saved, shown);
            case COLOR -> {
                Optional<String> a = colour(saved);
                Optional<String> b = colour(shown);
                yield a.isPresent() && a.equals(b);
            }
            default -> saved.equals(shown);
        };
    }

    private static boolean sameWhole(String saved, String shown) {
        try {
            return Long.parseLong(saved.strip()) == Long.parseLong(shown.strip());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Optional<String> colour(String text) {
        String hex = text.strip().toLowerCase(Locale.ROOT);
        hex = hex.startsWith("#") ? hex.substring(1) : hex;
        if (!hex.matches("[0-9a-f]{3}|[0-9a-f]{6}")) {
            return Optional.empty();
        }
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        return Optional.of(hex);
    }

    private static boolean sameNumber(String saved, String shown) {
        try {
            return new BigDecimal(saved.strip()).compareTo(new BigDecimal(shown.strip())) == 0;
        } catch (NumberFormatException e) {
            return saved.equals(shown);
        }
    }

    private Optional<String> sliderValue(PanelSection key, PanelAction action) {
        SettingSnapshot snapshot = latestOf(key, action.id);
        if (snapshot == null || action.number == null) {
            return Optional.empty();
        }
        SettingsSection.Kind kind = SettingsSection.kindOf(snapshot);
        if (kind != SettingsSection.Kind.INTEGER && kind != SettingsSection.Kind.DECIMAL) {
            return Optional.empty();
        }
        return Optional.of(
                SettingsSection.Slider.of(snapshot).text(action.number, kind == SettingsSection.Kind.INTEGER));
    }

    void onSettingChanged(PanelSection key, SettingsSection section, SettingChanged change) {
        if (!section.concerns(change)) {
            return;
        }
        Ref<EntityStore> ref = host.viewer().getReference();
        if (ref == null || !ref.isValid()) {
            host.stopListening();
            return;
        }
        Store<EntityStore> store = ref.getStore();
        store.getExternalData().getWorld().execute(() -> refresh(ref, store, key, section, change.id()));
    }

    void refresh(
            Ref<EntityStore> ref, Store<EntityStore> store, PanelSection key, SettingsSection section, SettingId id) {
        try {
            if (!host.isOpen(ref, store)) {
                host.stopListening();
                return;
            }
            SectionState state = states.get(key);
            Integer index = state.index(id);
            if (index == null) {
                return;
            }
            section.current(id).onSuccess(snapshot -> {
                if (state.shows(id, snapshot.value())) {
                    return;
                }
                state.remember(snapshot);
                if (SettingsSection.kindOf(snapshot) == SettingsSection.Kind.LIST) {
                    host.rebuild();
                }
                UICommandBuilder update = new UICommandBuilder();
                if (key == PanelSection.SERVER_CONFIG) {
                    showRestartNotice(update);
                }
                showRowValue(key, section, index, snapshot, update);
                if (!state.isInlineColor(id)) {
                    update.set(key.row(index) + " #Description.Text", section.description(snapshot));
                }
                state.clearUnsaved(id);
                showState(key, index, snapshot, update);
                footer(update, section.changedElsewhere(snapshot));
                host.showLegend(update);
                host.send(update);
            });
        } catch (RuntimeException e) {
            log.error("Could not refresh the control panel of " + host.viewer().getUsername(), e);
        }
    }

    private void showRowValue(
            PanelSection key, SettingsSection section, int index, SettingSnapshot snapshot, UICommandBuilder update) {
        if (states.get(key).isInlineColor(snapshot.id())) {
            SettingRows.showPicker(key.row(index), snapshot, update);
        } else {
            SettingRows.showValue(section, section.kind(snapshot), key.row(index), snapshot, update);
        }
    }

    void showRestartNotice(UICommandBuilder commands) {
        if (!sections.containsKey(PanelSection.SERVER_CONFIG)) {
            return;
        }
        List<Text> names = pendingRestart.get();
        restartNames = names.stream().map(tr::translate).collect(Collectors.toSet());
        commands.set("#RestartBanner.Visible", !names.isEmpty());
        commands.set(
                "#RestartText.Text",
                names.isEmpty()
                        ? ""
                        : tr.t(
                                "controlcore.panel.restart_pending",
                                "keys",
                                names.stream().map(tr::translate).collect(Collectors.joining(", "))));
        host.restartBanner(commands, !names.isEmpty());
    }

    void restoreDefaults(String sectionName) {
        Optional<PanelSection> found = Arrays.stream(PanelSection.values())
                .filter(candidate -> candidate.name().equals(sectionName))
                .findFirst();
        if (found.isEmpty()) {
            return;
        }
        PanelSection key = found.get();
        boolean permissions = key == PanelSection.PERMISSIONS;
        Optional<Set<String>> targets = permissions ? host.permissionTargets() : Optional.empty();
        if (permissions && targets.isEmpty()) {
            return;
        }
        DefaultsView.Section section = defaultsSection(key);
        Set<String> shown = permissions ? targets.get() : host.shownIds(key);
        int count = defaults.count(section, shown::contains);
        UICommandBuilder update = new UICommandBuilder();
        if (count == 0) {
            armedDefaults = Optional.empty();
            footer(update, tr.t("controlcore.panel.defaults.nothing"));
            host.send(update);
            return;
        }
        if (armedDefaults.filter(key::equals).isEmpty()) {
            armedDefaults = Optional.of(key);
            footer(
                    update,
                    tr.t(
                            defaults.savesFirst(section)
                                    ? "controlcore.panel.defaults.confirm_safe"
                                    : "controlcore.panel.defaults.confirm",
                            "count",
                            count));
            update.set(key.search + "Defaults.Text", tr.t("controlcore.panel.confirm"));
            host.send(update);
            return;
        }
        armedDefaults = Optional.empty();
        Result<String> restored = defaults.restore(section, shown::contains);
        if (restored.isFailure()) {
            footer(update, tr.error(restored.error()));
            host.send(update);
            return;
        }
        host.rebuild();
        UICommandBuilder after = new UICommandBuilder();
        footer(after, restored.value());
        showRestartNotice(after);
        host.send(after);
    }

    private static DefaultsView.Section defaultsSection(PanelSection key) {
        return switch (key) {
            case SERVER_CONFIG -> DefaultsView.Section.SERVER_CONFIGS;
            case SETTINGS -> DefaultsView.Section.MOD_SETTINGS;
            case PREFS -> DefaultsView.Section.PREFS;
            case KEYMAPS -> DefaultsView.Section.KEYMAPS_MINE;
            case SERVER_KEYMAPS -> DefaultsView.Section.KEYMAPS_SERVER;
            case PERMISSIONS -> DefaultsView.Section.PERMISSIONS;
        };
    }

    SettingSnapshot latestOf(PanelSection key, String idText) {
        return SettingId.parse(idText == null ? "" : idText)
                .map(id -> states.get(key).latest(id))
                .orElse(null);
    }

    Integer indexOf(PanelSection key, String idText) {
        return SettingId.parse(idText == null ? "" : idText)
                .map(id -> states.get(key).index(id))
                .orElse(null);
    }
}
