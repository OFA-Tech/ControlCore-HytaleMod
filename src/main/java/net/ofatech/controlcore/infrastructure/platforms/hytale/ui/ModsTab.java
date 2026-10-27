package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final Set<String> openConfigs = new HashSet<>();
    private Pending pending;

    private record Pending(String modId, boolean enabled, int row) {}

    ModsTab(ModsView view, Translator tr, Host host) {
        this.mods = new ModsSection(view, tr);
        this.tr = tr;
        this.host = host;
    }

    void build(
            UICommandBuilder commands, UIEventBuilder events, boolean settingsTab, boolean prefsTab, boolean keysTab) {
        List<ModsView.Entry> entries = mods.load();
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
        boolean admin = mods.view().canManage();
        for (int index = 0; index < entries.size(); index++) {
            ModsView.Entry entry = entries.get(index);
            ModInfo mod = entry.mod();
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Name.Text", mod.name());
            commands.set(row + " #Version.Text", mods.version(mod));
            showBadges(row, mods.badges(entry), commands);
            commands.set(row + " #Description.Text", mods.description(mod));
            commands.set(row + " #Details.Text", mods.details(mod));
            String needs = mods.needs(entry);
            commands.set(row + " #Needs.Visible", !needs.isEmpty());
            commands.set(row + " #Needs.Text", needs);
            String problems = mods.problems(entry);
            commands.set(row + " #Problem.Visible", !problems.isEmpty());
            commands.set(row + " #Problem.Text", problems);
            mod.logo().ifPresent(logo -> commands.set(row + " #ModLogo.Background", logo));
            commands.set(
                    row + " #Settings.Visible",
                    (entry.hasServerSettings() && settingsTab) || (entry.hasPreferences() && prefsTab));
            commands.set(row + " #Keys.Visible", entry.hasKeys() && keysTab);
            commands.set(row + " #Config.Visible", admin && mod.running() && !mod.core());
            commands.set(row + " #EnabledBox.Visible", entry.manageable());
            if (entry.manageable()) {
                commands.set(row + " #Enabled.Value", mod.enabledAfterRestart());
                events.addEventBinding(
                        CustomUIEventBindingType.ValueChanged,
                        row + " #Enabled",
                        button(ENABLE, mod)
                                .append(PanelAction.ITEM, Integer.toString(index))
                                .append(PanelAction.CHECKED, row + " #Enabled.Value"));
            }
            if (openConfigs.contains(mod.id())) {
                commands.set(row + " #ConfigText.Visible", true);
                commands.set(row + " #ConfigText.Text", mods.configText(mod.id()));
            }
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Settings", button(SETTINGS, mod));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Keys", button(KEYS, mod));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Config", button(CONFIG, mod));
        }
        applySearch(commands, false);
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
                mods.find(action.id)
                        .filter(entry -> entry.namespace().isPresent())
                        .ifPresent(entry -> host.showSettingsOf(entry, KEYS.equals(action.name())));
            case CONFIG -> toggleConfig(action.id);
            case ENABLE -> ask(action.id, Boolean.TRUE.equals(action.checked), action.item);
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
        if (modId == null || !mods.view().canManage()) {
            return;
        }
        if (!openConfigs.remove(modId)) {
            openConfigs.add(modId);
        }
        host.rebuild();
    }

    private void ask(String modId, boolean enabled, String item) {
        int row = item != null && item.matches("\\d+") ? Integer.parseInt(item) : -1;
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
