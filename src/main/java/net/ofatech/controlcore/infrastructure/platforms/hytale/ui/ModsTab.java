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
        boolean admin = mods.view().canManage();
        for (int index = 0; index < entries.size(); index++) {
            ModsView.Entry entry = entries.get(index);
            ModInfo mod = entry.mod();
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Name.Text", mod.name());
            commands.set(row + " #Version.Text", mods.version(mod));
            commands.set(row + " #Badges.Text", mods.badges(entry));
            commands.set(row + " #Description.Text", mods.description(mod));
            commands.set(row + " #Details.Text", mods.details(mod));
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
                        button(ENABLE, mod).append(PanelAction.CHECKED, row + " #Enabled.Value"));
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
            case ENABLE -> setEnabled(action.id, Boolean.TRUE.equals(action.checked));
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

    private void toggleConfig(String modId) {
        if (modId == null || !mods.view().canManage()) {
            return;
        }
        if (!openConfigs.remove(modId)) {
            openConfigs.add(modId);
        }
        host.rebuild();
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
