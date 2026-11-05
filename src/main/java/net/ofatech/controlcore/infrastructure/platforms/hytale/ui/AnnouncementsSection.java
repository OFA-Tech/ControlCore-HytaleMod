package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.automation.Announcements;
import net.ofatech.controlcore.domain.model.automation.AnnouncementsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class AnnouncementsSection {

    static final int LIST_HEIGHT = 440;

    private static final String TOGGLE_ALL = "AnnounceToggleAll";
    private static final String SAVE = "AnnounceSave";
    private static final String ADD = "AnnounceAdd";
    private static final String TOGGLE = "AnnounceItemToggle";
    private static final String UP = "AnnounceUp";
    private static final String DOWN = "AnnounceDown";
    private static final String SEND = "AnnounceSend";
    private static final String REMOVE = "AnnounceRemove";
    private static final String LIST = "#AnnounceList";
    private static final String ROW = "Pages/ControlCore/AnnouncementRow.ui";
    private static final List<String> SETTINGS = List.of("Interval", "Order", "Channel", "Min");
    private static final long CONFIRM_MILLIS = 10_000;

    private final AnnouncementsView view;
    private final Translator tr;
    private final AutomationTab.Host host;
    private final boolean withModes;
    private final ConfirmGate confirm = new ConfirmGate(CONFIRM_MILLIS);

    AnnouncementsSection(AnnouncementsView view, Translator tr, AutomationTab.Host host, boolean withModes) {
        this.view = view;
        this.tr = tr;
        this.host = host;
        this.withModes = withModes;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        Announcements state = view.state();
        long on = state.items().stream().filter(Announcements.Item::enabled).count();
        commands.set(
                "#AnnounceStatus.Text",
                tr.t(
                        state.enabled()
                                ? "controlcore.panel.announce.status_on"
                                : "controlcore.panel.announce.status_off",
                        "on",
                        on,
                        "total",
                        state.items().size()));
        commands.set(
                "#AnnounceToggle.Text",
                tr.t(state.enabled() ? "controlcore.panel.announce.turn_off" : "controlcore.panel.announce.turn_on"));
        commands.set("#AnnounceOrder.Entries", entries(view.orders()));
        commands.set("#AnnounceOrder.Value", state.order().id());
        commands.set("#AnnounceChannel.Entries", entries(view.channels()));
        commands.set("#AnnounceChannel.Value", state.channel().id());
        commands.set("#AnnounceInterval.Value", Integer.toString(state.intervalMinutes()));
        commands.set("#AnnounceMin.Value", Integer.toString(state.minPlayers()));
        commands.set("#AnnounceText.PlaceholderText", tr.t("controlcore.panel.announce.text_hint"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#AnnounceToggle", EventData.of(PanelAction.KEY, TOGGLE_ALL));
        EventData save = new EventData().append(PanelAction.KEY, SAVE);
        for (String field : SETTINGS) {
            save.append("@Announce" + field, "#Announce" + field + ".Value");
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AnnounceSave", save);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AnnounceAdd",
                new EventData().append(PanelAction.KEY, ADD).append("@AnnounceText", "#AnnounceText.Value"));
        List<Announcements.Item> items = state.items();
        commands.set("#AnnounceEmpty.Visible", items.isEmpty());
        for (int index = 0; index < items.size(); index++) {
            Announcements.Item item = items.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Number.Text", "#" + item.id());
            commands.set(row + " #Text.Text", item.text());
            commands.set(row + " #OnBadge.Visible", item.enabled());
            commands.set(row + " #OffBadge.Visible", !item.enabled());
            commands.set(
                    row + " #Toggle.Text",
                    tr.t(
                            item.enabled()
                                    ? "controlcore.panel.automation.turn_off"
                                    : "controlcore.panel.automation.turn_on"));
            commands.set(row + " #Up.Visible", index > 0);
            commands.set(row + " #Down.Visible", index < items.size() - 1);
            for (String[] button : List.of(
                    new String[] {"#Up", UP},
                    new String[] {"#Down", DOWN},
                    new String[] {"#Toggle", TOGGLE},
                    new String[] {"#Send", SEND},
                    new String[] {"#Remove", REMOVE})) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " " + button[0],
                        new EventData().append(PanelAction.KEY, button[1]).append(PanelAction.ID, item.id()));
            }
        }
        host.resize(LIST, LIST_HEIGHT - (withModes ? AutomationTab.MODES_HEIGHT : 0), commands);
        confirm.disarm();
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case TOGGLE_ALL -> refreshAfter(view.setEnabled(!view.state().enabled()));
            case SAVE ->
                refreshAfter(view.saveSettings(
                        action.form("AnnounceInterval"),
                        action.form("AnnounceOrder"),
                        action.form("AnnounceChannel"),
                        action.form("AnnounceMin")));
            case ADD -> refreshAfter(view.add(action.form("AnnounceText")));
            case TOGGLE -> refreshAfter(view.toggle(text(action.id)));
            case UP -> refreshAfter(view.move(text(action.id), -1));
            case DOWN -> refreshAfter(view.move(text(action.id), 1));
            case SEND -> footer(view.send(text(action.id)));
            case REMOVE -> remove(text(action.id));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void remove(String id) {
        if (!confirm.confirmed(id)) {
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t("controlcore.panel.announce.confirm_remove", "id", id));
            host.send(update);
            return;
        }
        refreshAfter(view.remove(id));
    }

    private void refreshAfter(Result<String> result) {
        if (result.isSuccess()) {
            host.rebuild();
        }
        footer(result);
    }

    private void footer(Result<String> result) {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.send(update);
    }

    private static List<DropdownEntryInfo> entries(List<AnnouncementsView.Choice> choices) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (AnnouncementsView.Choice choice : choices) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(choice.label()), choice.id()));
        }
        return entries;
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
