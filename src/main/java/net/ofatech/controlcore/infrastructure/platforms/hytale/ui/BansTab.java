package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.bans.BansView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class BansTab {

    private static final String ADD = "BanAdd";
    private static final String UNBAN = "BanRemove";
    private static final String ROW = "Pages/ControlCore/BanRow.ui";
    private static final String LIST = "#BanList";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void later(Runnable work);
    }

    private final BansView view;
    private final Translator tr;
    private final Host host;
    private String player = "";
    private String length = "permanent";
    private String reason = "";
    private Optional<String> armedUnban = Optional.empty();
    private Optional<String> footer = Optional.empty();

    BansTab(BansView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#BanPlayer.Value", player);
        commands.set("#BanPlayer.PlaceholderText", tr.t("controlcore.panel.bans.player_hint"));
        commands.set("#BanReason.Value", reason);
        commands.set("#BanReason.PlaceholderText", tr.t("controlcore.panel.bans.reason_hint"));
        commands.set(
                "#BanLength.Entries",
                view.lengths().stream()
                        .map(option -> new DropdownEntryInfo(LocalizableString.fromString(option.label()), option.id()))
                        .toList());
        commands.set("#BanLength.Value", length);
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BanAdd",
                new EventData()
                        .append(PanelAction.KEY, ADD)
                        .append(PanelAction.VALUE, "#BanPlayer.Value")
                        .append(PanelAction.FILTER, "#BanLength.Value")
                        .append(PanelAction.MATCH, "#BanReason.Value"));
        List<BansView.Entry> entries = view.list();
        commands.set("#BansEmpty.Visible", entries.isEmpty());
        for (int index = 0; index < entries.size(); index++) {
            BansView.Entry entry = entries.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Name.Text", entry.name());
            commands.set(row + " #Details.Text", entry.details());
            commands.set(row + " #Expired.Visible", !entry.active());
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #Unban",
                    new EventData().append(PanelAction.KEY, UNBAN).append(PanelAction.ID, entry.id()));
        }
        footer.ifPresent(text -> commands.set("#FooterStatus.Text", text));
        footer = Optional.empty();
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case ADD -> add(action);
            case UNBAN -> unban(action.id);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void add(PanelAction action) {
        player = action.value == null ? "" : action.value.strip();
        length = action.filter == null || action.filter.isBlank() ? "permanent" : action.filter;
        reason = action.match == null ? "" : action.match.strip();
        UICommandBuilder waiting = new UICommandBuilder();
        waiting.set("#FooterStatus.Text", tr.t("controlcore.bans.looking_up", "player", player));
        host.send(waiting);
        Consumer<Result<String>> done = result -> host.later(() -> {
            if (result.isSuccess()) {
                player = "";
                reason = "";
                footer = Optional.of(result.value());
                host.rebuild();
            } else {
                UICommandBuilder update = new UICommandBuilder();
                update.set("#FooterStatus.Text", tr.error(result.error()));
                host.send(update);
            }
        });
        view.ban(player, length, reason)
                .whenComplete((result, failure) -> done.accept(
                        failure == null
                                ? result
                                : Result.failure(net.ofatech.controlcore.domain.result.Error.failure(
                                        "Bans.Failed", String.valueOf(failure.getMessage())))));
    }

    private void unban(String id) {
        if (id == null) {
            host.send(new UICommandBuilder());
            return;
        }
        if (armedUnban.filter(id::equals).isEmpty()) {
            armedUnban = Optional.of(id);
            String name = view.list().stream()
                    .filter(entry -> entry.id().equals(id))
                    .map(BansView.Entry::name)
                    .findFirst()
                    .orElse(id);
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t("controlcore.panel.bans.confirm_unban", "player", name));
            host.send(update);
            return;
        }
        armedUnban = Optional.empty();
        Result<String> result = view.unban(id);
        if (result.isSuccess()) {
            footer = Optional.of(result.value());
            host.rebuild();
        } else {
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.error(result.error()));
            host.send(update);
        }
    }
}
