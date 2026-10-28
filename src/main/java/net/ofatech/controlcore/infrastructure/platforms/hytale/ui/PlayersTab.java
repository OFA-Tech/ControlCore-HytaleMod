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
    private static final String OPEN = "PlayerActionNew";
    private static final String CLOSE = "PlayerActionClose";
    private static final String KIND = "PlayerActionKind";
    private static final String BAN_KIND = "ban";
    private static final String UNBAN_KIND = "unban";
    private static final String KICK_KIND = "kick";
    private static final String GROUP = "PlayerActionGroup";
    private static final String DETAILS = "PlayerDetails";
    private static final String DETAILS_BACK = "PlayerDetailsBack";
    private static final String CHAT = "PlayerChat";
    private static final String BODY = "#PlayerDetailsBody";
    private static final List<String> NEEDS_LENGTH = List.of(BAN_KIND, "mute-add");
    private static final List<String> NEEDS_GROUP = List.of("group-add", "group-remove", "node-add", "node-remove");
    private static final List<String> NEEDS_NODE = List.of("node-add", "node-remove");
    private static final int LIST_OPEN = 400;
    private static final int LIST_CLOSED = 490;
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

        void resize(String element, int pixels, UICommandBuilder update);
    }

    private final PlayersView view;
    private final Translator tr;
    private final Host host;
    private String player = "";
    private String length = "permanent";
    private String reason = "";
    private PlayersView.Filter filter = PlayersView.Filter.ALL;
    private String query = "";
    private boolean formOpen;
    private String kind = BAN_KIND;
    private String group = "";
    private Optional<PlayersView.Details> details = Optional.empty();
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
        List<PlayersView.Choice> kinds = kinds(rights);
        if (kinds.stream().noneMatch(choice -> choice.id().equals(kind))) {
            kind = kinds.isEmpty() ? BAN_KIND : kinds.getFirst().id();
        }
        commands.set("#PlayerActionKind.Entries", entries(kinds));
        commands.set("#PlayerActionKind.Value", kind);
        List<PlayersView.Choice> groups = rights.groups() ? view.groups() : List.of();
        if (group.isEmpty() || groups.stream().noneMatch(choice -> choice.id().equals(group))) {
            group = groups.isEmpty() ? "" : groups.getFirst().id();
        }
        commands.set("#PlayerActionGroup.Entries", entries(groups));
        if (!group.isEmpty()) {
            commands.set("#PlayerActionGroup.Value", group);
        }
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
                CustomUIEventBindingType.Activating, "#PlayerActionNew", EventData.of(PanelAction.KEY, OPEN));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerChat", EventData.of(PanelAction.KEY, CHAT));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#PlayerDetailsBack", EventData.of(PanelAction.KEY, DETAILS_BACK));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#PlayerActionClose", EventData.of(PanelAction.KEY, CLOSE));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PlayerActionKind",
                new EventData().append(PanelAction.KEY, KIND).append(PanelAction.KEYS, "#PlayerActionKind.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PlayerActionGroup",
                new EventData().append(PanelAction.KEY, GROUP).append(PanelAction.NAME, "#PlayerActionGroup.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BanAdd",
                new EventData()
                        .append(PanelAction.KEY, ADD)
                        .append(PanelAction.KEYS, "#PlayerActionKind.Value")
                        .append(PanelAction.NAME, "#PlayerActionGroup.Value")
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
            commands.set(row + " #WhitelistBadge.Visible", entry.whitelisted());
            commands.set(row + " #OpBadge.Visible", entry.operator());
            commands.set(row + " #MutedBadge.Visible", entry.muted());
            commands.set(row + " #FrozenBadge.Visible", entry.frozen());
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
                    new String[] {"#Details", DETAILS},
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
        showForm(commands, !kinds.isEmpty());
        showDetails(commands);
        footer.ifPresent(text -> commands.set("#FooterStatus.Text", text));
        footer = Optional.empty();
    }

    private void showForm(UICommandBuilder commands, boolean allowed) {
        boolean open = formOpen && allowed;
        commands.set("#BanForm.Visible", open);
        commands.set("#PlayerActionNew.Visible", allowed && !open);
        commands.set("#BanLength.Visible", NEEDS_LENGTH.contains(kind));
        commands.set("#BanReason.Visible", BAN_KIND.equals(kind) || KICK_KIND.equals(kind));
        commands.set(
                "#BanReason.PlaceholderText",
                tr.t(
                        KICK_KIND.equals(kind)
                                ? "controlcore.players.kick_reason_hint"
                                : "controlcore.panel.bans.reason_hint"));
        commands.set("#PlayerActionGroup.Visible", NEEDS_GROUP.contains(kind));
        commands.set(
                "#BanPlayer.PlaceholderText",
                tr.t(
                        NEEDS_NODE.contains(kind)
                                ? "controlcore.players.node_hint"
                                : "controlcore.panel.bans.player_hint"));
        boolean note = NEEDS_NODE.contains(kind) && !group.isEmpty();
        commands.set("#PlayerActionNote.Visible", note && open);
        if (note) {
            List<String> nodes = view.groupPermissions(group);
            commands.set(
                    "#PlayerActionNote.Text",
                    nodes.isEmpty()
                            ? tr.t("controlcore.players.group_no_nodes")
                            : tr.t("controlcore.players.group_nodes", "nodes", String.join(", ", nodes)));
        }
        host.resize(LIST, open ? (note ? LIST_OPEN - 30 : LIST_OPEN) : LIST_CLOSED, commands);
    }

    private void showDetails(UICommandBuilder commands) {
        boolean open = details.isPresent();
        commands.set("#PlayerDetails.Visible", open);
        commands.set("#PlayerFilterRow.Visible", !open);
        commands.set("#BanList.Visible", !open);
        if (open) {
            commands.set("#BanForm.Visible", false);
            commands.set("#BansEmpty.Visible", false);
            PlayersView.Details shown = details.get();
            commands.set("#PlayerDetailsTitle.Text", shown.title());
            List<PlayersView.Line> lines = shown.lines();
            for (int index = 0; index < lines.size(); index++) {
                PlayersView.Line line = lines.get(index);
                commands.append(
                        BODY,
                        line.heading()
                                ? "Pages/ControlCore/PlayerDetailHeading.ui"
                                : "Pages/ControlCore/PlayerDetailLine.ui");
                commands.set(BODY + "[" + index + "].Text", line.text());
            }
        }
    }

    private List<PlayersView.Choice> kinds(PlayersView.Rights rights) {
        List<PlayersView.Choice> kinds = new ArrayList<>();
        if (rights.ban()) {
            kinds.add(new PlayersView.Choice(BAN_KIND, tr.t("controlcore.players.action.ban")));
            kinds.add(new PlayersView.Choice(UNBAN_KIND, tr.t("controlcore.players.action.unban")));
        }
        if (rights.kick()) {
            kinds.add(new PlayersView.Choice(KICK_KIND, tr.t("controlcore.players.action.kick")));
        }
        List<String> more = new ArrayList<>();
        if (rights.whitelist()) {
            more.addAll(List.of("whitelist-add", "whitelist-remove"));
        }
        if (rights.mute()) {
            more.addAll(List.of("mute-add", "mute-remove"));
        }
        if (rights.freeze()) {
            more.addAll(List.of("freeze-add", "freeze-remove"));
        }
        if (rights.groups()) {
            more.addAll(List.of("op-add", "op-remove", "group-add", "group-remove", "node-add", "node-remove"));
        }
        for (String id : more) {
            kinds.add(new PlayersView.Choice(id, tr.t("controlcore.players.action." + id)));
        }
        return kinds;
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
            case OPEN, CLOSE -> {
                formOpen = OPEN.equals(action.name());
                UICommandBuilder update = new UICommandBuilder();
                showForm(update, !kinds(view.rights()).isEmpty());
                host.send(update);
            }
            case DETAILS -> {
                host.send(new UICommandBuilder());
                String id = text(action.id);
                view.details(id)
                        .whenComplete((found, failure) -> host.later(() -> {
                            details = Optional.ofNullable(found);
                            host.rebuild();
                        }));
            }
            case CHAT -> {
                details = Optional.of(view.chat());
                host.rebuild();
            }
            case DETAILS_BACK -> {
                details = Optional.empty();
                host.rebuild();
            }
            case GROUP -> {
                group = text(action.bindName).isEmpty() ? group : text(action.bindName);
                UICommandBuilder update = new UICommandBuilder();
                showForm(update, !kinds(view.rights()).isEmpty());
                host.send(update);
            }
            case KIND -> {
                kind = text(action.bindKeys).isEmpty() ? kind : text(action.bindKeys);
                UICommandBuilder update = new UICommandBuilder();
                showForm(update, !kinds(view.rights()).isEmpty());
                host.send(update);
            }
            case FILTER, SEARCH -> narrow(action);
            case UNBAN -> confirmed(action, "controlcore.panel.bans.confirm_unban", () -> view.unban(action.id));
            case KICK ->
                confirmed(action, "controlcore.players.confirm_kick", () -> view.kick(action.id, text(action.match)));
            case BAN -> {
                formOpen = true;
                kind = BAN_KIND;
                player = rows.stream()
                        .filter(entry -> entry.id().equals(action.id))
                        .map(entry -> entry.name().isEmpty() ? entry.id() : entry.name())
                        .findFirst()
                        .orElse(text(action.id));
                UICommandBuilder update = new UICommandBuilder();
                update.set("#BanPlayer.Value", player);
                update.set("#PlayerActionKind.Value", kind);
                showForm(update, true);
                update.set("#FooterStatus.Text", tr.t("controlcore.players.ban_prefilled", "player", player));
                host.send(update);
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
        kind = text(action.bindKeys).isEmpty() ? kind : text(action.bindKeys);
        group = text(action.bindName).isEmpty() ? group : text(action.bindName);
        CompletableFuture<Result<String>> pending;
        if (kind.startsWith("mute-")) {
            pending = view.mute(player, length, kind.endsWith("-add"));
        } else if (kind.startsWith("freeze-")) {
            pending = view.freeze(player, kind.endsWith("-add"));
        } else if (kind.startsWith("whitelist-")) {
            pending = view.whitelist(player, kind.endsWith("-add"));
        } else if (kind.startsWith("op-")) {
            pending = view.operator(player, kind.endsWith("-add"));
        } else if (kind.startsWith("group-")) {
            pending = view.group(player, group, kind.endsWith("-add"));
        } else if (kind.startsWith("node-")) {
            pending = CompletableFuture.completedFuture(view.groupPermission(group, player, kind.endsWith("-add")));
        } else if (UNBAN_KIND.equals(kind)) {
            pending = CompletableFuture.completedFuture(view.unban(player));
        } else if (KICK_KIND.equals(kind)) {
            pending = CompletableFuture.completedFuture(rows.stream()
                    .filter(PlayersView.Entry::online)
                    .filter(entry ->
                            entry.name().equalsIgnoreCase(player) || entry.id().equalsIgnoreCase(player))
                    .findFirst()
                    .map(entry -> view.kick(entry.id(), reason))
                    .orElseGet(() -> Result.failure(Error.validation(
                            "Players.NotOnline",
                            net.ofatech.controlcore.domain.model.text.Text.key(
                                            "controlcore.errors.players.not_online", "{player} is not online.")
                                    .with("player", player)))));
        } else {
            UICommandBuilder waiting = new UICommandBuilder();
            waiting.set("#FooterStatus.Text", tr.t("controlcore.bans.looking_up", "player", player));
            host.send(waiting);
            pending = view.ban(player, length, reason);
        }
        finish(pending.thenApply(result -> {
            if (result.isSuccess()) {
                player = "";
                reason = "";
                formOpen = false;
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
