package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

final class PlayersTab {

    private static final String ADD = "BanAdd";
    private static final String UNBAN = "BanRemove";
    private static final String BAN = "PlayerBan";
    private static final String KICK = "PlayerKick";
    private static final String GAME_MODE = "PlayerGameMode";
    private static final String TELEPORT_TO = "PlayerTeleportTo";
    private static final String BRING = "PlayerBring";
    private static final String FILTER = "PlayerFilter";
    private static final String SEARCH = "PlayerSearch";
    private static final String ROW = "Pages/ControlCore/PlayerRow.ui";
    private static final String LIST = "#BanList";
    private static final int AVATAR_COLOURS = 8;

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void later(Runnable work);

        PlayerRef viewer();
    }

    private final PlayersView view;
    private final Translator tr;
    private final Host host;
    private String player = "";
    private String length = "permanent";
    private String reason = "";
    private PlayersView.Filter filter = PlayersView.Filter.ALL;
    private String query = "";
    private Optional<String> armed = Optional.empty();
    private Optional<String> footer = Optional.empty();
    private List<PlayersView.Entry> rows = List.of();
    private int generation;

    PlayersTab(PlayersView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    PlayersView view() {
        return view;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        PlayersView.Rights rights = view.rights();
        commands.set("#BanForm.Visible", rights.ban());
        commands.set("#BanPlayer.Value", player);
        commands.set("#BanPlayer.PlaceholderText", tr.t("controlcore.panel.bans.player_hint"));
        commands.set("#BanReason.Value", reason);
        commands.set("#BanReason.PlaceholderText", tr.t("controlcore.panel.bans.reason_hint"));
        commands.set("#BanLength.Entries", entries(view.lengths()));
        commands.set("#BanLength.Value", length);
        commands.set("#PlayerFilter.Entries", entries(view.filters()));
        commands.set("#PlayerFilter.Value", filter.id());
        commands.set("#PlayerSearch.Value", query);
        commands.set("#PlayerSearch.PlaceholderText", tr.t("controlcore.players.search_hint"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BanAdd",
                new EventData()
                        .append(PanelAction.KEY, ADD)
                        .append(PanelAction.VALUE, "#BanPlayer.Value")
                        .append(PanelAction.FILTER, "#BanLength.Value")
                        .append(PanelAction.MATCH, "#BanReason.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PlayerFilter",
                new EventData().append(PanelAction.KEY, FILTER).append(PanelAction.VALUE, "#PlayerFilter.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PlayerSearch",
                new EventData().append(PanelAction.KEY, SEARCH).append(PanelAction.VALUE, "#PlayerSearch.Value"));
        rows = view.list();
        int built = ++generation;
        boolean heads = view.showHeads();
        String self = view.viewer();
        for (int index = 0; index < rows.size(); index++) {
            PlayersView.Entry entry = rows.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(
                    row + " #Name.Text",
                    entry.name().isEmpty() ? tr.t("controlcore.panel.bans.unknown_name") : entry.name());
            commands.set(row + " #Uuid.Text", entry.id());
            commands.set(row + " #Initial.Text", entry.initial());
            int colour = entry.colour(AVATAR_COLOURS);
            for (int shade = 0; shade < AVATAR_COLOURS; shade++) {
                commands.set(row + " #Avatar" + shade + ".Visible", shade == colour);
            }
            commands.set(row + " #OnlineBadge.Visible", entry.online());
            commands.set(row + " #OfflineBadge.Visible", !entry.online());
            commands.set(row + " #BannedBadge.Visible", entry.banned());
            commands.set(row + " #Status.Text", entry.status());
            commands.set(row + " #BanInfo.Text", entry.ban());
            commands.set(row + " #BanInfo.Visible", !entry.ban().isEmpty());
            boolean other = !entry.id().equals(self);
            commands.set(row + " #GameMode.Visible", entry.online() && rights.gameMode());
            commands.set(row + " #TeleportTo.Visible", entry.online() && other && rights.teleport());
            commands.set(row + " #Bring.Visible", entry.online() && other && rights.teleport());
            commands.set(row + " #Kick.Visible", entry.online() && other && rights.kick());
            commands.set(row + " #Ban.Visible", entry.ban().isEmpty() && other && rights.ban());
            commands.set(row + " #Unban.Visible", !entry.ban().isEmpty() && rights.ban());
            commands.set(row + ".Visible", entry.matches(filter, query));
            for (String[] button : List.of(
                    new String[] {"#GameMode", GAME_MODE},
                    new String[] {"#TeleportTo", TELEPORT_TO},
                    new String[] {"#Bring", BRING},
                    new String[] {"#Kick", KICK},
                    new String[] {"#Ban", BAN},
                    new String[] {"#Unban", UNBAN})) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " " + button[0],
                        new EventData()
                                .append(PanelAction.KEY, button[1])
                                .append(PanelAction.ID, entry.id())
                                .append(PanelAction.FILTER, "#BanLength.Value")
                                .append(PanelAction.MATCH, "#BanReason.Value"));
            }
            if (entry.online()) {
                view.live(entry.id())
                        .thenAccept(text -> text.ifPresent(found -> host.later(() -> {
                            if (built == generation) {
                                UICommandBuilder update = new UICommandBuilder();
                                update.set(row + " #Status.Text", found);
                                host.send(update);
                            }
                        })));
            }
            if (entry.name().isEmpty()) {
                view.resolve(entry.id())
                        .thenAccept(found -> found.ifPresent(name -> host.later(() -> {
                            if (built != generation) {
                                return;
                            }
                            UICommandBuilder update = new UICommandBuilder();
                            update.set(row + " #Name.Text", name);
                            update.set(
                                    row + " #Initial.Text", name.substring(0, 1).toUpperCase(Locale.ROOT));
                            host.send(update);
                            if (heads) {
                                showHead(row, name, built);
                            }
                        })));
            } else if (heads) {
                showHead(row, entry.name(), built);
            }
        }
        showCount(commands);
        footer.ifPresent(text -> commands.set("#FooterStatus.Text", text));
        footer = Optional.empty();
    }

    private void showCount(UICommandBuilder commands) {
        long shown = rows.stream().filter(entry -> entry.matches(filter, query)).count();
        commands.set("#BansEmpty.Visible", shown == 0);
        commands.set("#BansEmpty.Text", tr.t("controlcore.players.empty"));
        commands.set("#PlayerCount.Text", tr.t("controlcore.players.count", "shown", shown, "total", rows.size()));
    }

    private void showHead(String row, String name, int built) {
        PlayerHeads.show(
                host.viewer(),
                name,
                texture -> host.later(() -> {
                    if (built != generation) {
                        return;
                    }
                    UICommandBuilder update = new UICommandBuilder();
                    update.set(row + " #Head.Background", texture);
                    update.set(row + " #Head.Visible", true);
                    update.set(row + " #Initial.Visible", false);
                    for (int shade = 0; shade < AVATAR_COLOURS; shade++) {
                        update.set(row + " #Avatar" + shade + ".Visible", false);
                    }
                    host.send(update);
                }));
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case ADD -> add(action);
            case FILTER, SEARCH -> narrow(action);
            case UNBAN -> confirmed(action, "controlcore.panel.bans.confirm_unban", () -> view.unban(action.id));
            case KICK ->
                confirmed(action, "controlcore.players.confirm_kick", () -> view.kick(action.id, text(action.match)));
            case BAN -> {
                remember(action);
                finish(view.ban(action.id, length, reason));
            }
            case GAME_MODE -> finish(view.toggleGameMode(action.id));
            case TELEPORT_TO -> finish(view.teleportTo(action.id));
            case BRING -> finish(view.bring(action.id));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void narrow(PanelAction action) {
        if (FILTER.equals(action.name())) {
            filter = PlayersView.Filter.of(action.value);
        } else {
            query = text(action.value);
        }
        UICommandBuilder update = new UICommandBuilder();
        for (int index = 0; index < rows.size(); index++) {
            update.set(LIST + "[" + index + "].Visible", rows.get(index).matches(filter, query));
        }
        showCount(update);
        host.send(update);
    }

    private void add(PanelAction action) {
        player = text(action.value);
        remember(action);
        UICommandBuilder waiting = new UICommandBuilder();
        waiting.set("#FooterStatus.Text", tr.t("controlcore.bans.looking_up", "player", player));
        host.send(waiting);
        finish(view.ban(player, length, reason).thenApply(result -> {
            if (result.isSuccess()) {
                player = "";
                reason = "";
            }
            return result;
        }));
    }

    private void remember(PanelAction action) {
        length = action.filter == null || action.filter.isBlank() ? "permanent" : action.filter;
        reason = text(action.match);
    }

    private void confirmed(PanelAction action, String question, java.util.function.Supplier<Result<String>> work) {
        if (action.id == null) {
            host.send(new UICommandBuilder());
            return;
        }
        String key = action.name() + ":" + action.id;
        if (armed.filter(key::equals).isEmpty()) {
            armed = Optional.of(key);
            String name = rows.stream()
                    .filter(entry -> entry.id().equals(action.id))
                    .map(PlayersView.Entry::name)
                    .filter(found -> !found.isEmpty())
                    .findFirst()
                    .orElse(action.id);
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t(question, "player", name));
            host.send(update);
            return;
        }
        armed = Optional.empty();
        if (KICK.equals(action.name())) {
            reason = text(action.match);
        }
        show(work.get());
    }

    private void finish(CompletableFuture<Result<String>> pending) {
        armed = Optional.empty();
        host.send(new UICommandBuilder());
        pending.whenComplete((result, failure) -> host.later(() -> show(
                failure == null ? result : Result.failure(Error.failure("Players.Failed", String.valueOf(failure))))));
    }

    private void show(Result<String> result) {
        if (result.isSuccess()) {
            footer = Optional.of(result.value());
            host.rebuild();
        } else {
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.error(result.error()));
            host.send(update);
        }
    }

    private static List<DropdownEntryInfo> entries(List<PlayersView.Choice> choices) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        for (PlayersView.Choice choice : choices) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(choice.label()), choice.id()));
        }
        return entries;
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
