package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.mods.ModsView;
import net.ofatech.controlcore.domain.model.text.Translator;

final class ModsTab {

    private static final String SEARCH = "ModsSearch";
    private static final String SETTINGS = "ModSettings";
    private static final String KEYS = "ModKeys";
    private static final String CONFIG = "ModConfig";
    private static final String ENABLE = "ModEnable";
    private static final String CONFIRM = "ModConfirm";
    private static final String CANCEL = "ModCancel";
    private static final String INFO = "ModInfo";
    private static final String BACK = "ModInfoBack";
    private static final String CONFIG_BACK = "ModConfigBack";
    private static final String OPEN = "ModInfoOpen";
    private static final String HEADER = "#ModDetailsHeader[0]";
    private static final String DEP_ROW = "Pages/ControlCore/ModDepRow.ui";
    private static final int SLOTS = 10;
    private static final String DETAILS_ITEM = "details";
    private static final String ROW = "Pages/ControlCore/ModRow.ui";
    private static final String LIST = "#ModsList";
    private static final String STATUS = "#ModsStatus.Text";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void showSettingsOf(ModsView.Entry entry, boolean keys);

        void showRestartNotice(UICommandBuilder update);
    }

    private final ModsSection mods;
    private final Translator tr;
    private final Host host;
    private String configOf;
    private Pending pending;
    private String detailsOf;
    private final java.util.Map<String, String> slotTargets = new java.util.HashMap<>();
    private boolean settingsTab;
    private boolean prefsTab;
    private boolean keysTab;

    private record Pending(String modId, boolean enabled, int row) {}

    ModsTab(ModsView view, Translator tr, Host host) {
        this.mods = new ModsSection(view, tr);
        this.tr = tr;
        this.host = host;
    }

    void build(
            UICommandBuilder commands, UIEventBuilder events, boolean settingsTab, boolean prefsTab, boolean keysTab) {
        List<ModsView.Entry> entries = mods.load();
        this.settingsTab = settingsTab;
        this.prefsTab = prefsTab;
        this.keysTab = keysTab;
        commands.set(
                "#ModsSearchFilter.Entries",
                Arrays.stream(ModsSection.Filter.values())
                        .map(option -> new DropdownEntryInfo(
                                LocalizableString.fromString(tr.t(option.translationKey())), option.name()))
                        .toList());
        commands.set("#ModsSearchFilter.Value", mods.filter().name());
        commands.set("#ModsSearch.Value", mods.query());
        commands.set("#ModsSearch.PlaceholderText", tr.t("controlcore.panel.mods.search_hint"));
        EventData search = new EventData()
                .append(PanelAction.KEY, SEARCH)
                .append(PanelAction.VALUE, "#ModsSearch.Value")
                .append(PanelAction.FILTER, "#ModsSearchFilter.Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ModsSearch", search);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ModsSearchFilter", search);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#ModsConfirmYes", EventData.of(PanelAction.KEY, CONFIRM));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#ModsConfirmNo", EventData.of(PanelAction.KEY, CANCEL));
        pending = null;
        commands.set("#ModsConfirm.Visible", false);
        for (int index = 0; index < entries.size(); index++) {
            ModsView.Entry entry = entries.get(index);
            ModInfo mod = entry.mod();
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            fillRow(row, entry, commands);
            if (entry.manageable()) {
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        row + " #Enabled",
                        button(ENABLE, mod)
                                .append(PanelAction.ITEM, Integer.toString(index))
                                .append(PanelAction.CHECKED, row + " #Enabled.Value"));
            }
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Settings", button(SETTINGS, mod));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Keys", button(KEYS, mod));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Config", button(CONFIG, mod));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Info", button(INFO, mod));
        }
        applySearch(commands, false);
        buildDetails(commands, events);
    }

    private void fillRow(String row, ModsView.Entry entry, UICommandBuilder commands) {
        ModInfo mod = entry.mod();
        boolean admin = mods.view().canManage();
        commands.set(row + " #Name.Text", mod.name());
        commands.set(row + " #Version.Text", mods.version(mod));
        showBadges(row, mods.badges(entry), commands);
        commands.set(row + " #Description.Text", mods.description(mod));
        commands.set(row + " #Details.Text", mods.details(mod));
        String problems = mods.problems(entry);
        commands.set(row + " #Problem.Visible", !problems.isEmpty());
        commands.set(row + " #Problem.Text", problems);
        commands.set(row + " #ModLogo.Background", mod.logo().orElse("ControlCore_ModIcon.png"));
        commands.set(
                row + " #Settings.Visible",
                (entry.hasServerSettings() && settingsTab) || (entry.hasPreferences() && prefsTab));
        commands.set(row + " #Keys.Visible", entry.hasKeys() && keysTab);
        commands.set(row + " #Config.Visible", admin && mod.running() && !mod.core());
        commands.set(row + " #Info.Visible", !row.equals(HEADER));
        commands.set(row + " #EnabledBox.Visible", entry.manageable());
        if (entry.manageable()) {
            commands.set(row + " #Enabled.Value", mod.enabledAfterRestart());
        }
    }

    private void buildDetails(UICommandBuilder commands, UIEventBuilder events) {
        commands.append("#ModDetailsHeader", ROW);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#ModDetailsBack", EventData.of(PanelAction.KEY, BACK));
        for (String[] button : List.of(
                new String[] {"#Settings", SETTINGS}, new String[] {"#Keys", KEYS}, new String[] {"#Config", CONFIG})) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    HEADER + " " + button[0],
                    new EventData().append(PanelAction.KEY, button[1]).append(PanelAction.ITEM, DETAILS_ITEM));
        }
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                HEADER + " #Enabled",
                new EventData()
                        .append(PanelAction.KEY, ENABLE)
                        .append(PanelAction.ITEM, DETAILS_ITEM)
                        .append(PanelAction.CHECKED, HEADER + " #Enabled.Value"));
        for (String section : List.of("MdRequires", "MdCanUse", "MdNeededBy")) {
            for (int slot = 0; slot < SLOTS; slot++) {
                addSlot(commands, events, "#" + section, section + ":" + slot, slot);
            }
        }
        addSlot(commands, events, "#MdHytale", "MdHytale:0", 0);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#ModConfigBack", EventData.of(PanelAction.KEY, CONFIG_BACK));
        if (detailsOf != null) {
            fillDetails(commands);
        }
        if (configOf != null && !fillConfig(commands)) {
            configOf = null;
        }
        views(commands);
    }

    private void addSlot(UICommandBuilder commands, UIEventBuilder events, String container, String key, int slot) {
        commands.append(container, DEP_ROW);
        String row = container + "[" + slot + "]";
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Open",
                new EventData().append(PanelAction.KEY, OPEN).append(PanelAction.ITEM, key));
        commands.set(row + " #Ok.Text", tr.t("controlcore.panel.mods.dep.ok"));
        commands.set(row + " #Missing.Text", tr.t("controlcore.panel.mods.dep.missing"));
        commands.set(row + " #Off.Text", tr.t("controlcore.panel.mods.dep.off"));
        commands.set(row + " #Wrong.Text", tr.t("controlcore.panel.mods.dep.wrong"));
        commands.set(row + " #Absent.Text", tr.t("controlcore.panel.mods.dep.absent"));
    }

    private enum Mark {
        OK,
        MISSING,
        OFF,
        WRONG,
        ABSENT,
        INSTALLED,
        ON
    }

    private record Line(String name, Optional<String> target, String need, String have, Mark mark) {}

    private void fillDetails(UICommandBuilder commands) {
        Optional<ModsView.Entry> found = mods.find(detailsOf);
        if (found.isEmpty()) {
            detailsOf = null;
            views(commands);
            return;
        }
        ModsView.Entry entry = found.get();
        ModInfo mod = entry.mod();
        fillRow(HEADER, entry, commands);
        slotTargets.clear();
        List<Line> requires = new java.util.ArrayList<>();
        List<Line> canUse = new java.util.ArrayList<>();
        for (ModDependency dependency : mod.dependencies()) {
            Optional<ModsView.Entry> installed = mods.find(dependency.id());
            String name = installed.map(other -> other.mod().name()).orElse(dependency.id());
            String need = dependency.range().isEmpty()
                    ? tr.t("controlcore.panel.mods.dep.any")
                    : tr.t("controlcore.panel.mods.dep.needs", "range", dependency.range());
            String have = installed
                    .map(other -> tr.t("controlcore.panel.mods.dep.installed", "version", mods.version(other.mod())))
                    .orElse(tr.t("controlcore.panel.mods.dep.not_installed"));
            Optional<String> target = installed.map(other -> other.mod().id());
            if (dependency.optional()) {
                canUse.add(new Line(name, target, need, have, installed.isPresent() ? Mark.INSTALLED : Mark.ABSENT));
            } else {
                Mark mark = switch (dependency.status()) {
                    case OK -> Mark.OK;
                    case MISSING -> Mark.MISSING;
                    case OFF -> Mark.OFF;
                    case WRONG_VERSION -> Mark.WRONG;
                };
                requires.add(new Line(name, target, need, have, mark));
            }
        }
        List<Line> neededBy = new java.util.ArrayList<>();
        for (String other : entry.neededBy()) {
            Optional<ModsView.Entry> byName = mods.entries().stream()
                    .filter(candidate -> candidate.mod().name().equalsIgnoreCase(other)
                            || candidate.mod().id().equalsIgnoreCase(other))
                    .findFirst();
            boolean on = byName.map(candidate -> candidate.mod().running()).orElse(false);
            neededBy.add(new Line(
                    byName.map(candidate -> candidate.mod().name()).orElse(other),
                    byName.map(candidate -> candidate.mod().id()),
                    byName.map(candidate -> mods.version(candidate.mod())).orElse(""),
                    "",
                    on ? Mark.ON : Mark.OFF));
        }
        fillSection(commands, "MdRequires", requires);
        fillSection(commands, "MdCanUse", canUse);
        fillSection(commands, "MdNeededBy", neededBy);
        String running = com.hypixel.hytale.common.util.java.ManifestUtil.getVersion();
        fillSlot(
                commands,
                "#MdHytale[0]",
                "MdHytale:0",
                new Line(
                        "Hytale",
                        Optional.empty(),
                        mod.serverVersion().isEmpty()
                                ? tr.t("controlcore.panel.mods.dep.any")
                                : tr.t("controlcore.panel.mods.dep.needs", "range", mod.serverVersion()),
                        tr.t("controlcore.panel.mods.dep.server", "version", running == null ? "?" : running),
                        mod.serverVersionOk() ? Mark.OK : Mark.WRONG));
    }

    private void fillSection(UICommandBuilder commands, String section, List<Line> lines) {
        commands.set("#" + section + "None.Visible", lines.isEmpty());
        for (int slot = 0; slot < SLOTS; slot++) {
            String row = "#" + section + "[" + slot + "]";
            if (slot < lines.size()) {
                fillSlot(commands, row, section + ":" + slot, lines.get(slot));
            } else {
                commands.set(row + ".Visible", false);
            }
        }
        int more = lines.size() - SLOTS;
        commands.set("#" + section + "More.Visible", more > 0);
        if (more > 0) {
            commands.set("#" + section + "More.Text", tr.t("controlcore.panel.mods.dep.more", "count", more));
        }
    }

    private void fillSlot(UICommandBuilder commands, String row, String key, Line line) {
        commands.set(row + ".Visible", true);
        boolean link = line.target().isPresent() && !line.target().get().equalsIgnoreCase(detailsOf);
        commands.set(row + " #Open.Visible", link);
        commands.set(row + " #Name.Visible", !link);
        commands.set(row + " #Open.Text", line.name());
        commands.set(row + " #Name.Text", line.name());
        commands.set(row + " #Need.Text", line.need());
        commands.set(row + " #Have.Text", line.have());
        commands.set(
                row + " #Ok.Visible",
                line.mark() == Mark.OK || line.mark() == Mark.INSTALLED || line.mark() == Mark.ON);
        commands.set(
                row + " #Ok.Text",
                tr.t(
                        line.mark() == Mark.INSTALLED
                                ? "controlcore.panel.mods.dep.installed_badge"
                                : line.mark() == Mark.ON
                                        ? "controlcore.panel.mods.dep.on"
                                        : "controlcore.panel.mods.dep.ok"));
        commands.set(row + " #Missing.Visible", line.mark() == Mark.MISSING);
        commands.set(row + " #Off.Visible", line.mark() == Mark.OFF);
        commands.set(row + " #Wrong.Visible", line.mark() == Mark.WRONG);
        commands.set(row + " #Absent.Visible", line.mark() == Mark.ABSENT);
        line.target().ifPresent(target -> slotTargets.put(key, target));
    }

    private void showDetails(String modId) {
        UICommandBuilder update = new UICommandBuilder();
        if (modId == null || mods.find(modId).isEmpty()) {
            host.send(update);
            return;
        }
        detailsOf = mods.find(modId).get().mod().id();
        configOf = null;
        fillDetails(update);
        views(update);
        host.send(update);
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case SEARCH -> {
                mods.search(action.value, ModsSection.Filter.of(action.filter));
                UICommandBuilder update = new UICommandBuilder();
                applySearch(update, true);
                host.send(update);
            }
            case SETTINGS, KEYS ->
                mods.find(target(action))
                        .filter(entry -> entry.namespace().isPresent())
                        .ifPresent(entry -> host.showSettingsOf(entry, KEYS.equals(action.name())));
            case CONFIG -> toggleConfig(target(action));
            case ENABLE -> ask(target(action), Boolean.TRUE.equals(action.checked), action.item);
            case INFO -> showDetails(action.id);
            case OPEN -> showDetails(slotTargets.get(action.item == null ? "" : action.item));
            case BACK -> {
                detailsOf = null;
                UICommandBuilder update = new UICommandBuilder();
                views(update);
                host.send(update);
            }
            case CONFIG_BACK -> {
                configOf = null;
                UICommandBuilder update = new UICommandBuilder();
                views(update);
                host.send(update);
            }
            case CONFIRM -> {
                Pending wanted = pending;
                pending = null;
                if (wanted == null) {
                    host.send(new UICommandBuilder());
                } else {
                    List<ModsView.Entry> others = mods.view().alsoChanged(wanted.modId(), wanted.enabled());
                    setEnabled(wanted.modId(), wanted.enabled());
                    if (!others.isEmpty()) {
                        UICommandBuilder update = new UICommandBuilder();
                        update.set(
                                "#FooterStatus.Text",
                                tr.t(
                                        "controlcore.panel.mods.also_changed",
                                        "name",
                                        mods.find(wanted.modId())
                                                .map(entry -> entry.mod().name())
                                                .orElse(wanted.modId()),
                                        "count",
                                        others.size(),
                                        "mods",
                                        String.join(
                                                ", ",
                                                others.stream()
                                                        .map(entry ->
                                                                entry.mod().name())
                                                        .toList())));
                        host.send(update);
                    }
                }
            }
            case CANCEL -> cancel();
            default -> {
                return false;
            }
        }
        return true;
    }

    private String target(PanelAction action) {
        return DETAILS_ITEM.equals(action.item) ? detailsOf : action.id;
    }

    private void applySearch(UICommandBuilder commands, boolean report) {
        List<ModsView.Entry> entries = mods.entries();
        for (int index = 0; index < entries.size(); index++) {
            commands.set(LIST + "[" + index + "].Visible", mods.shown(entries.get(index)));
        }
        int shownCount = mods.shownCount();
        if (shownCount == 0) {
            commands.set(STATUS, tr.t("controlcore.panel.mods.none"));
        } else if (report) {
            commands.set(STATUS, tr.t("controlcore.panel.mods.shown", "shown", shownCount));
        } else {
            commands.set(STATUS, "");
        }
    }

    private void showBadges(String row, ModsSection.Badges badges, UICommandBuilder commands) {
        commands.set(row + " #BadgeCode.Visible", badges.code());
        commands.set(row + " #BadgeNoCode.Visible", !badges.code());
        commands.set(row + " #BadgeAssets.Visible", badges.assets());
        commands.set(row + " #BadgeNoAssets.Visible", !badges.assets());
        commands.set(row + " #BadgeCode.Text", tr.t("controlcore.panel.mods.badge.code"));
        commands.set(row + " #BadgeNoCode.Text", tr.t("controlcore.panel.mods.badge.no_code"));
        commands.set(row + " #BadgeAssets.Text", tr.t("controlcore.panel.mods.badge.assets"));
        commands.set(row + " #BadgeNoAssets.Text", tr.t("controlcore.panel.mods.badge.no_assets"));
        commands.set(row + " #BadgeMissing.Visible", badges.missing() > 0);
        commands.set(
                row + " #BadgeMissing.Text", tr.t("controlcore.panel.mods.badge.missing", "count", badges.missing()));
        commands.set(row + " #BadgeWrongVersion.Visible", badges.wrongVersion() > 0);
        commands.set(row + " #BadgeWrongVersion.Text", tr.t("controlcore.panel.mods.badge.wrong_version"));
        commands.set(row + " #BadgeNeeded.Visible", badges.neededBy() > 0);
        commands.set(
                row + " #BadgeNeeded.Text", tr.t("controlcore.panel.mods.badge.needed_by", "count", badges.neededBy()));
        for (String state : java.util.List.of("Off", "Failed", "Restart")) {
            boolean on = state.equals(badges.state());
            commands.set(row + " #Badge" + state + ".Visible", on);
            if (on) {
                commands.set(row + " #Badge" + state + ".Text", badges.stateText());
            }
        }
        commands.set(row + " #BadgeHytale.Visible", badges.hytale());
        if (badges.hytale()) {
            commands.set(row + " #BadgeHytale.Text", tr.t("controlcore.panel.mods.badge.hytale"));
        }
    }

    private void toggleConfig(String modId) {
        UICommandBuilder update = new UICommandBuilder();
        if (modId != null && mods.view().canManage()) {
            configOf = mods.find(modId).map(entry -> entry.mod().id()).orElse(null);
            if (configOf != null && !fillConfig(update)) {
                configOf = null;
            }
        }
        views(update);
        host.send(update);
    }

    private boolean fillConfig(UICommandBuilder commands) {
        Optional<ModsView.Entry> entry = mods.find(configOf);
        if (entry.isEmpty()) {
            return false;
        }
        commands.set(
                "#ModConfigTitle.Text",
                tr.t(
                        "controlcore.panel.mods.config_title",
                        "name",
                        entry.get().mod().name()));
        commands.set("#ModConfigText.Text", mods.configText(configOf));
        return true;
    }

    private void views(UICommandBuilder commands) {
        commands.set("#ModConfigView.Visible", configOf != null);
        commands.set("#ModDetails.Visible", configOf == null && detailsOf != null);
        commands.set("#ModsListView.Visible", configOf == null && detailsOf == null);
    }

    private void ask(String modId, boolean enabled, String item) {
        int row = DETAILS_ITEM.equals(item) ? -2 : item != null && item.matches("\\d+") ? Integer.parseInt(item) : -1;
        List<ModsView.Entry> others = modId == null ? List.of() : mods.view().alsoChanged(modId, enabled);
        if (others.isEmpty()) {
            pending = null;
            setEnabled(modId, enabled);
            return;
        }
        String name = mods.find(modId).map(entry -> entry.mod().name()).orElse(modId);
        String names = String.join(
                ", ", others.stream().map(entry -> entry.mod().name()).toList());
        pending = new Pending(modId, enabled, row);
        UICommandBuilder update = new UICommandBuilder();
        update.set("#ModsConfirm.Visible", true);
        update.set(
                "#ModsConfirmText.Text",
                tr.t(
                        enabled ? "controlcore.panel.mods.confirm_on" : "controlcore.panel.mods.confirm_off",
                        "name",
                        name,
                        "mods",
                        names));
        update.set(
                "#ModsConfirmYes.Text",
                tr.t(enabled ? "controlcore.panel.mods.confirm_all_on" : "controlcore.panel.mods.confirm_all_off"));
        update.set("#FooterStatus.Text", tr.t("controlcore.panel.mods.confirm_hint"));
        host.send(update);
    }

    private void cancel() {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#ModsConfirm.Visible", false);
        if (pending != null && pending.row() >= 0) {
            update.set(LIST + "[" + pending.row() + "] #Enabled.Value", !pending.enabled());
        } else if (pending != null && pending.row() == -2) {
            update.set(HEADER + " #Enabled.Value", !pending.enabled());
        }
        pending = null;
        update.set("#FooterStatus.Text", tr.t("controlcore.panel.mods.confirm_cancelled"));
        host.send(update);
    }

    private void setEnabled(String modId, boolean enabled) {
        var result = mods.view().setEnabled(modId, enabled);
        if (result.isSuccess()) {
            host.rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(
                "#FooterStatus.Text",
                result.isSuccess()
                        ? tr.t(
                                enabled
                                        ? "controlcore.panel.mods.enabled_restart"
                                        : "controlcore.panel.mods.disabled_restart",
                                "name",
                                result.value().mod().name())
                        : tr.error(result.error()));
        host.showRestartNotice(update);
        host.send(update);
    }

    private static EventData button(String name, ModInfo mod) {
        return new EventData().append(PanelAction.KEY, name).append(PanelAction.ID, mod.id());
    }
}
