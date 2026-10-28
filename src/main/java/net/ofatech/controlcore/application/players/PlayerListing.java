package net.ofatech.controlcore.application.players;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.application.automation.SessionService;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.text.PlainText;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.players.PlayersView.Details;
import net.ofatech.controlcore.domain.model.players.PlayersView.Entry;
import net.ofatech.controlcore.domain.model.players.PlayersView.Line;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.AccessLists;
import net.ofatech.controlcore.domain.ports.game.Bans.Ban;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerListing {

    private final Players players;
    private final PlayerData data;
    private final PlayerDirectory directory;
    private final PlayerModeration moderation;
    private final PlayerAdmin admin;
    private final AccessLists access;
    private final SessionService sessions;
    private final BansService bans;
    private final RecentChat chat;
    private final Clock clock;

    public PlayerListing(
            Players players,
            PlayerData data,
            PlayerDirectory directory,
            PlayerModeration moderation,
            PlayerAdmin admin,
            AccessLists access,
            SessionService sessions,
            BansService bans,
            RecentChat chat,
            Clock clock) {
        this.players = players;
        this.data = data;
        this.directory = directory;
        this.moderation = moderation;
        this.admin = admin;
        this.access = access;
        this.sessions = sessions;
        this.bans = bans;
        this.chat = chat;
        this.clock = clock;
    }

    List<Entry> list(Translator tr) {
        Map<PlayerId, String> names = new LinkedHashMap<>();
        Set<PlayerId> online = new HashSet<>();
        for (PlayerInfo player : players.online()) {
            names.put(player.id(), player.name());
            online.add(player.id());
        }
        data.all().forEach((id, values) -> {
            if (values.containsKey(SessionService.FIRST_JOIN)
                    || values.containsKey(PlayerModeration.MUTED_UNTIL)
                    || values.containsKey(PlayerModeration.FROZEN)) {
                String name = values.get(SessionService.NAME);
                names.putIfAbsent(id, name == null ? bans.knownName(id).orElse("") : name);
            }
        });
        Set<PlayerId> whitelisted = access.whitelisted();
        whitelisted.forEach(id -> names.putIfAbsent(id, bans.knownName(id).orElse("")));
        Map<PlayerId, Ban> banned = new HashMap<>();
        for (Ban ban : bans.all()) {
            banned.putIfAbsent(ban.target(), ban);
            names.putIfAbsent(ban.target(), bans.knownName(ban.target()).orElse(""));
        }
        Instant now = clock.now();
        String operators = access.operatorGroup();
        List<Entry> entries = new ArrayList<>();
        names.forEach((id, name) -> {
            boolean isOnline = online.contains(id);
            Ban ban = banned.get(id);
            Set<String> groups = access.groupsOf(id);
            entries.add(new Entry(
                    id.value().toString(),
                    name == null ? "" : name,
                    isOnline,
                    ban != null && ban.inEffect(now),
                    whitelisted.contains(id),
                    groups.contains(operators),
                    moderation.activeMute(id).isPresent(),
                    moderation.frozen(id),
                    status(id, isOnline, tr) + groupsText(groups, tr),
                    ban == null ? "" : banText(ban, now, tr)));
        });
        entries.sort(Comparator.comparing((Entry entry) -> !entry.online())
                .thenComparing(entry -> entry.name().isEmpty())
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return entries;
    }

    private String banText(Ban ban, Instant now, Translator tr) {
        return tr.t(
                ban.inEffect(now) ? "controlcore.players.ban.active" : "controlcore.players.ban.expired",
                "details",
                bans.details(ban, tr));
    }

    private String status(PlayerId id, boolean online, Translator tr) {
        String played = playtime(id);
        if (online) {
            return tr.t(
                    "controlcore.players.status.online",
                    "world",
                    sessions.world(id).orElse("-"),
                    "played",
                    played);
        }
        Optional<Instant> first = instant(id, SessionService.FIRST_JOIN);
        if (first.isEmpty()) {
            return tr.t("controlcore.players.status.unknown");
        }
        return tr.t(
                "controlcore.players.status.offline",
                "when",
                instant(id, SessionService.LAST_SEEN)
                        .or(() -> first)
                        .map(PlainText::when)
                        .orElse("-"),
                "played",
                played);
    }

    CompletableFuture<Optional<String>> live(PlayerId id, Translator tr) {
        if (!directory.online(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        OptionalInt ping = admin.ping(id);
        return admin.gameMode(id)
                .thenApply(mode -> Optional.of(tr.t(
                                "controlcore.players.status.live",
                                "world",
                                sessions.world(id).orElse("-"),
                                "mode",
                                mode.map(found -> PlayerControl.modeName(found, tr))
                                        .orElse("?"),
                                "ping",
                                ping.isPresent()
                                        ? tr.t("controlcore.players.ping", "ms", ping.getAsInt())
                                        : tr.t("controlcore.players.ping_unknown"),
                                "played",
                                playtime(id))
                        + groupsText(access.groupsOf(id), tr)));
    }

    private String groupsText(Set<String> groups, Translator tr) {
        return groups.isEmpty() ? "" : " · " + tr.t("controlcore.players.groups", "groups", labels(groups));
    }

    private String labels(Set<String> groups) {
        return String.join(", ", groups.stream().map(access::label).toList());
    }

    CompletableFuture<Result<Details>> details(PlayerId id, boolean inventory, Translator tr) {
        List<Line> lines = new ArrayList<>();
        boolean online = directory.online(id);
        String name = directory.nameOf(id);
        lines.add(new Line(tr.t("controlcore.players.details.about"), true));
        lines.add(new Line(
                tr.t("controlcore.players.details.uuid", "uuid", id.value().toString()), false));
        lines.add(new Line(status(id, online, tr), false));
        Set<String> groups = access.groupsOf(id);
        lines.add(new Line(
                tr.t("controlcore.players.details.groups", "groups", groups.isEmpty() ? "-" : labels(groups)), false));
        lines.add(new Line(
                tr.t(
                        "controlcore.players.details.whitelist",
                        "value",
                        tr.t(
                                access.whitelisted().contains(id)
                                        ? "controlcore.players.value.yes"
                                        : "controlcore.players.value.no")),
                false));
        instant(id, SessionService.FIRST_JOIN)
                .ifPresent(first -> lines.add(new Line(
                        tr.t("controlcore.players.details.first_join", "when", PlainText.when(first)), false)));
        bans.find(id).ifPresent(ban -> lines.add(new Line(bans.details(ban, tr), false)));
        moderation.activeMute(id).ifPresent(until -> lines.add(new Line(moderation.shownMute(until, tr), false)));
        if (moderation.frozen(id)) {
            lines.add(new Line(tr.t("controlcore.players.details.frozen"), false));
        }
        lines.add(new Line(tr.t("controlcore.players.details.chat"), true));
        List<RecentChat.Line> theirs = chat.of(id, RecentChat.PER_PLAYER);
        if (theirs.isEmpty()) {
            lines.add(new Line(tr.t("controlcore.players.details.no_chat"), false));
        }
        theirs.forEach(line -> lines.add(new Line(PlainText.clock(line.at()) + "  " + line.text(), false)));
        if (!inventory) {
            return CompletableFuture.completedFuture(Result.success(new Details(name, lines)));
        }
        lines.add(new Line(tr.t("controlcore.players.details.inventory"), true));
        if (!online) {
            lines.add(new Line(tr.t("controlcore.players.details.inventory_offline"), false));
            return CompletableFuture.completedFuture(Result.success(new Details(name, lines)));
        }
        return admin.inventory(id).thenApply(items -> {
            List<Line> all = new ArrayList<>(lines);
            if (items.isEmpty()) {
                all.add(new Line(tr.t("controlcore.players.details.inventory_offline"), false));
            } else if (items.get().isEmpty()) {
                all.add(new Line(tr.t("controlcore.players.details.inventory_empty"), false));
            } else {
                items.get()
                        .forEach(item -> all.add(new Line(
                                tr.t("controlcore.players.details.item", "item", item.id(), "count", item.quantity()),
                                false)));
            }
            return Result.success(new Details(name, all));
        });
    }

    Details chatView(Translator tr) {
        List<RecentChat.Line> recent = chat.last(RecentChat.SHOWN);
        List<Line> lines = new ArrayList<>();
        if (recent.isEmpty()) {
            lines.add(new Line(tr.t("controlcore.players.chat.empty"), false));
        }
        recent.forEach(line ->
                lines.add(new Line(PlainText.clock(line.at()) + "  " + line.name() + ": " + line.text(), false)));
        return new Details(tr.t("controlcore.players.chat.title"), lines);
    }

    private String playtime(PlayerId id) {
        Duration played = sessions.find(id).map(session -> session.playtime()).orElse(Duration.ZERO);
        long minutes = played.toMinutes();
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    private Optional<Instant> instant(PlayerId id, String key) {
        try {
            return data.get(id, key).map(Instant::parse);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
