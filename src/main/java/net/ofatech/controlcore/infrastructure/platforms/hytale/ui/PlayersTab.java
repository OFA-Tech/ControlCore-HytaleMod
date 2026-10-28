package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

final class PlayersTab {

    private static final String DETAILS = "PlayerDetails";
    private static final String DETAILS_BACK = "PlayerDetailsBack";
    private static final String CHAT = "PlayerChat";
    private static final String UNBAN = "PlayerUnban";
    private static final String BAN = "PlayerBan";
    private static final String KICK = "PlayerKick";
    private static final String GAME_MODE = "PlayerGameMode";
    private static final String TELEPORT_TO = "PlayerTeleportTo";
    private static final String BRING = "PlayerBring";
    private static final String FILTER = "PlayerFilter";
    private static final String SEARCH = "PlayerSearch";
    private static final String BODY = "#PlayerDetailsBody";
    private static final String ROW = "Pages/ControlCore/PlayerRow.ui";
    private static final String LIST = "#PlayerList";
    private static final int LIST_WITH_FORM = 400;
    private static final int NOTE_HEIGHT = 30;
    private static final int LIST_ALONE = 490;
    private static final int AVATAR_COLOURS = 8;
    private static final int HEADS_SHOWN = 24;
    private static final long QUIET_MILLIS = 2_000;
    private static final long CONFIRM_MILLIS = 5_000;
    private static final long ACTION_TIMEOUT_SECONDS = 20;
    private static final List<String[]> ROW_BUTTONS = List.of(
            new String[] {"#Details", DETAILS},
            new String[] {"#GameMode", GAME_MODE},
            new String[] {"#TeleportTo", TELEPORT_TO},
            new String[] {"#Bring", BRING},
            new String[] {"#Kick", KICK},
            new String[] {"#Ban", BAN},
            new String[] {"#Unban", UNBAN});

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
    private final Log log;
    private final PlayerActionForm form;
    private PlayersView.Filter filter = PlayersView.Filter.ALL;
    private String query = "";
    private Optional<PlayersView.Details> details = Optional.empty();
    private int detailsRequest;
    private Optional<String> armed = Optional.empty();
    private long armedAt;
    private Optional<String> footer = Optional.empty();
    private List<PlayersView.Entry> rows = List.of();
    private int generation;
    private final Set<String> pendingRows = new HashSet<>();
    private boolean stale;
    private long lastTyped;

    PlayersTab(PlayersView view, Translator tr, Host host, Log log) {
        this.view = view;
        this.tr = tr;
        this.host = host;
        this.log = log;
        this.form = new PlayerActionForm(view, tr);
    }

    PlayersView view() {
        return view;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        stale = false;
        form.build(commands, events);
        commands.set("#PlayerFilter.Entries", entries(view.filters()));
        commands.set("#PlayerFilter.Value", filter.id());
        commands.set("#PlayerSearch.Value", query);
        commands.set("#PlayerSearch.PlaceholderText", tr.t("controlcore.players.search_hint"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerChat", EventData.of(PanelAction.KEY, CHAT));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#PlayerDetailsBack", EventData.of(PanelAction.KEY, DETAILS_BACK));
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
        int headsLeft = HEADS_SHOWN;
        for (int index = 0; index < rows.size(); index++) {
            boolean head = heads && headsLeft > 0 && rows.get(index).matches(filter, query);
            if (head) {
                headsLeft--;
            }
            buildRow(commands, events, index, built, head);
        }
        showCount(commands);
        resizeList(commands);
        showDetails(commands);
        footer.ifPresent(text -> commands.set("#FooterStatus.Text", text));
        footer = Optional.empty();
    }

    private void buildRow(UICommandBuilder commands, UIEventBuilder events, int index, int built, boolean heads) {
        PlayersView.Entry entry = rows.get(index);
        PlayersView.Rights rights = view.rights();
        String row = LIST + "[" + index + "]";
        commands.append(LIST, ROW);
        commands.set(
                row + " #Name.Text",
                entry.name().isEmpty() ? tr.t("controlcore.panel.players.unknown_name") : entry.name());
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
        boolean other = !entry.id().equals(view.viewer());
        commands.set(row + " #GameMode.Visible", entry.online() && rights.gameMode());
        commands.set(row + " #TeleportTo.Visible", entry.online() && other && rights.teleport());
        commands.set(row + " #Bring.Visible", entry.online() && other && rights.teleport());
        commands.set(row + " #Kick.Visible", entry.online() && other && rights.kick());
        commands.set(row + " #Ban.Visible", !entry.banned() && other && rights.ban());
        commands.set(row + " #Unban.Visible", !entry.ban().isEmpty() && rights.ban());
        commands.set(row + ".Visible", entry.matches(filter, query));
        for (String[] button : ROW_BUTTONS) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " " + button[0],
                    new EventData().append(PanelAction.KEY, button[1]).append(PanelAction.ID, entry.id()));
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
                        update.set(row + " #Initial.Text", name.substring(0, 1).toUpperCase(Locale.ROOT));
                        host.send(update);
                        if (heads) {
                            showHead(row, name, built);
                        }
                    })));
        } else if (heads) {
            showHead(row, entry.name(), built);
        }
    }

    private void resizeList(UICommandBuilder commands) {
        int height = form.isOpen() ? LIST_WITH_FORM - (form.showsNote() ? NOTE_HEIGHT : 0) : LIST_ALONE;
        host.resize(LIST, height, commands);
    }

    private void showDetails(UICommandBuilder commands) {
        boolean open = details.isPresent();
        commands.set("#PlayerDetails.Visible", open);
        commands.set("#PlayerFilterRow.Visible", !open);
        commands.set(LIST + ".Visible", !open);
        if (!open) {
            return;
        }
        commands.set(PlayerActionForm.FORM + ".Visible", false);
        commands.set("#PlayerListEmpty.Visible", false);
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

    private void showCount(UICommandBuilder commands) {
        long shown = rows.stream().filter(entry -> entry.matches(filter, query)).count();
        commands.set("#PlayerListEmpty.Visible", shown == 0);
        commands.set("#PlayerListEmpty.Text", tr.t("controlcore.players.empty"));
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
        if (form.handle(
                action,
                this::sendFormUpdate,
                pending -> finish(pending.orTimeout(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS), form::finished))) {
            return true;
        }
        switch (action.name()) {
            case DETAILS -> openDetails(action);
            case CHAT -> {
                armed = Optional.empty();
                detailsRequest++;
                details = Optional.of(view.chat());
                host.rebuild();
            }
            case DETAILS_BACK -> {
                detailsRequest++;
                details = Optional.empty();
                host.rebuild();
            }
            case FILTER, SEARCH -> narrow(action);
            case UNBAN -> confirmed(action, "controlcore.panel.players.confirm_unban", () -> view.unban(action.id));
            case KICK -> confirmed(action, "controlcore.players.confirm_kick", () -> view.kick(action.id, ""));
            case BAN -> {
                armed = Optional.empty();
                if (form.busy()) {
                    showError(PlayerErrorText.busy(tr));
                    return true;
                }
                form.prefillBan(nameOf(text(action.id)));
                footer = Optional.of(tr.t("controlcore.players.ban_prefilled", "player", nameOf(text(action.id))));
                host.rebuild();
            }
            case GAME_MODE -> rowAction(action, () -> view.toggleGameMode(action.id));
            case TELEPORT_TO -> rowAction(action, () -> view.teleportTo(action.id));
            case BRING -> rowAction(action, () -> view.bring(action.id));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void sendFormUpdate(UICommandBuilder update) {
        resizeList(update);
        host.send(update);
        if (stale && !form.isOpen()) {
            host.rebuild();
        }
    }

    boolean refreshNow() {
        boolean typing = System.currentTimeMillis() - lastTyped < QUIET_MILLIS;
        if (form.isOpen() || typing || !pendingRows.isEmpty()) {
            boolean first = !stale;
            stale = true;
            if (first && typing && !form.isOpen()) {
                retryWhenQuiet();
            }
            return false;
        }
        return true;
    }

    private void retryWhenQuiet() {
        CompletableFuture.delayedExecutor(QUIET_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> host.later(() -> {
                    if (stale && refreshNow()) {
                        host.rebuild();
                    }
                }));
    }

    private void rowAction(PanelAction action, Supplier<CompletableFuture<Result<String>>> work) {
        String key = action.name() + ":" + text(action.id);
        if (!pendingRows.add(key)) {
            showError(PlayerErrorText.busy(tr));
            return;
        }
        CompletableFuture<Result<String>> pending;
        try {
            pending = work.get().orTimeout(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            pendingRows.remove(key);
            throw e;
        }
        finish(pending, result -> pendingRows.remove(key));
    }

    private void openDetails(PanelAction action) {
        armed = Optional.empty();
        int request = ++detailsRequest;
        String id = text(action.id);
        UICommandBuilder waiting = new UICommandBuilder();
        waiting.set("#FooterStatus.Text", tr.t("controlcore.players.details.loading", "player", nameOf(id)));
        host.send(waiting);
        view.details(id)
                .whenComplete((result, failure) -> host.later(() -> {
                    if (request != detailsRequest) {
                        return;
                    }
                    Result<PlayersView.Details> found = failure == null ? result : Result.failure(failed(failure));
                    if (found.isSuccess()) {
                        details = Optional.of(found.value());
                        host.rebuild();
                    } else {
                        showError(found.error());
                    }
                }));
    }

    private void narrow(PanelAction action) {
        armed = Optional.empty();
        if (FILTER.equals(action.name())) {
            filter = PlayersView.Filter.of(action.value);
        } else {
            query = text(action.value);
            lastTyped = System.currentTimeMillis();
        }
        UICommandBuilder update = new UICommandBuilder();
        for (int index = 0; index < rows.size(); index++) {
            update.set(LIST + "[" + index + "].Visible", rows.get(index).matches(filter, query));
        }
        showCount(update);
        host.send(update);
    }

    private void confirmed(PanelAction action, String question, Supplier<Result<String>> work) {
        if (action.id == null) {
            host.send(new UICommandBuilder());
            return;
        }
        String key = action.name() + ":" + action.id;
        boolean fresh = System.currentTimeMillis() - armedAt < CONFIRM_MILLIS;
        if (armed.filter(key::equals).isEmpty() || !fresh) {
            armed = Optional.of(key);
            armedAt = System.currentTimeMillis();
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t(question, "player", nameOf(action.id)));
            host.send(update);
            return;
        }
        armed = Optional.empty();
        show(work.get());
    }

    private void finish(CompletableFuture<Result<String>> pending, Consumer<Result<String>> done) {
        armed = Optional.empty();
        host.send(new UICommandBuilder());
        pending.whenComplete((result, failure) -> host.later(() -> {
            Result<String> outcome = failure == null ? result : Result.failure(failed(failure));
            done.accept(outcome);
            show(outcome);
            if (stale && outcome.isFailure() && refreshNow()) {
                host.rebuild();
            }
        }));
    }

    private Error failed(Throwable failure) {
        log.error("A Players panel action failed", failure);
        return Error.failure("Players.Failed", tr.t("controlcore.panel.failed"));
    }

    private void show(Result<String> result) {
        if (result.isSuccess() && form.isOpen()) {
            stale = true;
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", result.value());
            host.send(update);
        } else if (result.isSuccess()) {
            footer = Optional.of(result.value());
            host.rebuild();
        } else {
            showError(result.error());
        }
    }

    private void showError(Error error) {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", tr.error(error));
        host.send(update);
    }

    private String nameOf(String id) {
        return rows.stream()
                .filter(entry -> entry.id().equals(id))
                .map(entry -> entry.name().isEmpty() ? entry.id() : entry.name())
                .findFirst()
                .orElse(id);
    }

    static List<DropdownEntryInfo> entries(List<PlayersView.Choice> choices) {
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
