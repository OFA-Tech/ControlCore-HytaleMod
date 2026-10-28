package net.ofatech.controlcore.application.players;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.automation.SessionService;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.players.PlayersView.Entry;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.AccessLists;
import net.ofatech.controlcore.domain.ports.game.Bans.Ban;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayersService {

    public static final String NODE = "players";
    public static final String KICK = "players.kick";
    public static final String GAME_MODE = "players.gamemode";
    public static final String TELEPORT = "players.teleport";
    public static final String WHITELIST = "players.whitelist";
    public static final String TARGET = "player:";
    static final String GAME_MODE_TARGET = TARGET + "gamemode:";
    static final String KICK_TARGET = TARGET + "kick:";
    static final String WHITELIST_TARGET = TARGET + "whitelist:";
    static final String GROUP_TARGET = TARGET + "group:";
    static final String GROUP_PERM_TARGET = TARGET + "groupperm:";
    private static final String SEPARATOR = "|";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TOAST_SECONDS = 6;

    private final BansService bans;
    private final Players players;
    private final SessionService sessions;
    private final PlayerData data;
    private final PlayerAdmin admin;
    private final AccessLists access;
    private final ChangeHistory history;
    private final SettingPermissionPolicy policy;
    private final Permissions permissions;
    private final Texts texts;
    private final NotificationService notifications;
    private final Clock clock;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public PlayersService(
            BansService bans,
            Players players,
            SessionService sessions,
            PlayerData data,
            PlayerAdmin admin,
            AccessLists access,
            ChangeHistory history,
            SettingPermissionPolicy policy,
            Permissions permissions,
            Texts texts,
            NotificationService notifications,
            Clock clock) {
        this.bans = bans;
        this.players = players;
        this.sessions = sessions;
        this.data = data;
        this.admin = admin;
        this.access = access;
        this.history = history;
        this.policy = policy;
        this.permissions = permissions;
        this.texts = texts;
        this.notifications = notifications;
        this.clock = clock;
        bans.onChange(this::changed);
    }

    public void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException listenerFailed) {
                listeners.remove(listener);
            }
        }
    }

    public boolean maySee(Optional<PlayerId> who) {
        return may(who, NODE) || bans.mayManage(who);
    }

    private boolean may(Optional<PlayerId> who, String node) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), node);
    }

    List<Entry> list(Translator tr) {
        Map<PlayerId, String> names = new LinkedHashMap<>();
        Map<PlayerId, Boolean> online = new HashMap<>();
        for (PlayerInfo player : players.online()) {
            names.put(player.id(), player.name());
            online.put(player.id(), true);
        }
        data.all().forEach((id, values) -> {
            if (values.containsKey(SessionService.FIRST_JOIN)) {
                String name = values.get(SessionService.NAME);
                names.putIfAbsent(id, name == null ? bans.knownName(id).orElse("") : name);
            }
        });
        java.util.Set<PlayerId> whitelisted = access.whitelisted();
        for (PlayerId id : whitelisted) {
            names.putIfAbsent(id, bans.knownName(id).orElse(""));
        }
        Map<PlayerId, Ban> banned = new HashMap<>();
        for (Ban ban : bans.all()) {
            banned.putIfAbsent(ban.target(), ban);
            names.putIfAbsent(ban.target(), bans.knownName(ban.target()).orElse(""));
        }
        Instant now = clock.now();
        List<Entry> entries = new ArrayList<>();
        names.forEach((id, name) -> {
            boolean isOnline = online.containsKey(id);
            Ban ban = banned.get(id);
            String banText = ban == null
                    ? ""
                    : tr.t(
                            ban.inEffect(now) ? "controlcore.players.ban.active" : "controlcore.players.ban.expired",
                            "details",
                            bans.details(ban, tr));
            java.util.Set<String> groups = access.groupsOf(id);
            entries.add(new Entry(
                    id.value().toString(),
                    name == null ? "" : name,
                    isOnline,
                    ban != null && ban.inEffect(now),
                    whitelisted.contains(id),
                    groups.contains(AccessLists.OPERATOR_GROUP),
                    status(id, isOnline, tr) + groupsText(groups, tr),
                    banText));
        });
        entries.sort(Comparator.comparing((Entry entry) -> !entry.online())
                .thenComparing(entry -> entry.name().isEmpty())
                .thenComparing(entry -> entry.name().toLowerCase(java.util.Locale.ROOT)));
        return entries;
    }

    private String status(PlayerId id, boolean online, Translator tr) {
        Optional<Instant> first = instant(id, SessionService.FIRST_JOIN);
        String played = playtime(id);
        if (online) {
            return tr.t(
                    "controlcore.players.status.online",
                    "world",
                    sessions.world(id).orElse("-"),
                    "played",
                    played);
        }
        if (first.isEmpty()) {
            return tr.t("controlcore.players.status.unknown");
        }
        return tr.t(
                "controlcore.players.status.offline",
                "when",
                instant(id, SessionService.LAST_SEEN)
                        .or(() -> first)
                        .map(this::when)
                        .orElse("-"),
                "played",
                played);
    }

    CompletableFuture<Optional<String>> live(PlayerId id, Translator tr) {
        if (players.find(id).isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        OptionalInt ping = admin.ping(id);
        return admin.gameMode(id)
                .thenApply(mode -> Optional.of(tr.t(
                                "controlcore.players.status.live",
                                "world",
                                sessions.world(id).orElse("-"),
                                "mode",
                                mode.map(found -> modeName(found, tr)).orElse("?"),
                                "ping",
                                ping.isPresent()
                                        ? tr.t("controlcore.players.ping", "ms", ping.getAsInt())
                                        : tr.t("controlcore.players.ping_unknown"),
                                "played",
                                playtime(id))
                        + groupsText(access.groupsOf(id), tr)));
    }

    private static String groupsText(java.util.Set<String> groups, Translator tr) {
        if (groups.isEmpty()) {
            return "";
        }
        return " · "
                + tr.t(
                        "controlcore.players.groups",
                        "groups",
                        String.join(
                                ", ",
                                groups.stream().map(PlayersService::groupName).toList()));
    }

    static String groupName(String group) {
        return group.startsWith("hytale:") ? group.substring("hytale:".length()) : group;
    }

    public CompletableFuture<Result<String>> setWhitelisted(
            Optional<PlayerId> who, String player, boolean add, Translator tr) {
        if (!may(who, WHITELIST)) {
            return CompletableFuture.completedFuture(Result.failure(forbidden()));
        }
        return target(player).thenApply(found -> {
            if (found.isEmpty()) {
                return Result.<String>failure(unknown(player));
            }
            PlayerId id = found.get();
            boolean before = access.whitelisted().contains(id);
            if (before == add) {
                return Result.<String>failure(Error.validation(
                        "Players.NothingToDo",
                        Text.key(
                                        add
                                                ? "controlcore.errors.players.already_whitelisted"
                                                : "controlcore.errors.players.not_whitelisted",
                                        add
                                                ? "{player} is already on the whitelist."
                                                : "{player} is not on the whitelist.")
                                .with("player", nameOf(id))));
            }
            boolean ok = add ? access.allow(id) : access.disallow(id);
            if (!ok) {
                return Result.<String>failure(failed());
            }
            history.record(
                    Kind.PLAYER,
                    WHITELIST_TARGET + id.value() + ":" + nameOf(id),
                    String.valueOf(before),
                    String.valueOf(add),
                    false);
            changed();
            return Result.success(tr.t(
                    add ? "controlcore.players.whitelisted" : "controlcore.players.unwhitelisted",
                    "player",
                    nameOf(id)));
        });
    }

    public CompletableFuture<Result<String>> setGroup(
            Optional<PlayerId> who, String player, String group, boolean add, Translator tr) {
        if (!mayChangeGroups(who)) {
            return CompletableFuture.completedFuture(Result.failure(adminsOnly()));
        }
        String wanted = group == null ? "" : group.strip();
        if (wanted.isEmpty() || !access.groups().contains(wanted)) {
            return CompletableFuture.completedFuture(Result.failure(Error.validation(
                    "Players.UnknownGroup",
                    Text.key("controlcore.errors.players.unknown_group", "There is no group {group}.")
                            .with("group", wanted))));
        }
        return target(player).thenApply(found -> {
            if (found.isEmpty()) {
                return Result.<String>failure(unknown(player));
            }
            PlayerId id = found.get();
            if (!add
                    && wanted.equals(AccessLists.OPERATOR_GROUP)
                    && who.filter(id::equals).isPresent()) {
                return Result.<String>failure(self());
            }
            boolean before = access.groupsOf(id).contains(wanted);
            if (before == add) {
                return Result.<String>failure(Error.validation(
                        "Players.NothingToDo",
                        Text.key(
                                        add
                                                ? "controlcore.errors.players.already_in_group"
                                                : "controlcore.errors.players.not_in_group",
                                        add ? "{player} is already in {group}." : "{player} is not in {group}.")
                                .with("player", nameOf(id), "group", groupName(wanted))));
            }
            if (add) {
                access.addToGroup(id, wanted);
            } else {
                access.removeFromGroup(id, wanted);
            }
            history.record(
                    Kind.PLAYER,
                    GROUP_TARGET + id.value() + ":" + nameOf(id) + SEPARATOR + wanted,
                    String.valueOf(before),
                    String.valueOf(add),
                    false);
            changed();
            return Result.success(tr.t(
                    add ? "controlcore.players.group_added" : "controlcore.players.group_removed",
                    "player",
                    nameOf(id),
                    "group",
                    groupName(wanted)));
        });
    }

    public Result<String> setGroupPermission(
            Optional<PlayerId> who, String group, String node, boolean add, Translator tr) {
        if (!mayChangeGroups(who)) {
            return Result.failure(adminsOnly());
        }
        String wanted = group == null ? "" : group.strip();
        String permission = node == null ? "" : node.strip();
        if (wanted.isEmpty() || !access.groups().contains(wanted)) {
            return Result.failure(Error.validation(
                    "Players.UnknownGroup",
                    Text.key("controlcore.errors.players.unknown_group", "There is no group {group}.")
                            .with("group", wanted)));
        }
        if (!permission.matches("-?[A-Za-z0-9_.*:-]{1,120}")) {
            return Result.failure(Error.validation(
                    "Players.BadNode",
                    Text.key(
                                    "controlcore.errors.players.bad_node",
                                    "'{node}' is not a permission node. Use letters, digits, dots, - and *.")
                            .with("node", permission)));
        }
        boolean before = access.groupPermissions(wanted).contains(permission);
        if (before == add) {
            return Result.failure(Error.validation(
                    "Players.NothingToDo",
                    Text.key(
                                    add
                                            ? "controlcore.errors.players.group_has_node"
                                            : "controlcore.errors.players.group_lacks_node",
                                    add ? "{group} already has {node}." : "{group} doesn't have {node}.")
                            .with("group", groupName(wanted), "node", permission)));
        }
        if (add) {
            access.addGroupPermission(wanted, permission);
        } else {
            access.removeGroupPermission(wanted, permission);
        }
        history.record(
                Kind.PLAYER,
                GROUP_PERM_TARGET + wanted + SEPARATOR + permission,
                String.valueOf(before),
                String.valueOf(add),
                false);
        changed();
        return Result.success(tr.t(
                add ? "controlcore.players.node_added" : "controlcore.players.node_removed",
                "group",
                groupName(wanted),
                "node",
                permission));
    }

    private boolean mayChangeGroups(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get());
    }

    private CompletableFuture<Optional<PlayerId>> target(String player) {
        String input = player == null ? "" : player.strip();
        if (input.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerId> online = players.online().stream()
                .filter(info -> info.name().equalsIgnoreCase(input)
                        || info.id().value().toString().equalsIgnoreCase(input))
                .map(PlayerInfo::id)
                .findFirst();
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online);
        }
        Optional<PlayerId> parsed = parse(input);
        if (parsed.isPresent() && data.all().containsKey(parsed.get())) {
            return CompletableFuture.completedFuture(parsed);
        }
        for (var known : data.all().entrySet()) {
            if (input.equalsIgnoreCase(known.getValue().get(SessionService.NAME))) {
                return CompletableFuture.completedFuture(Optional.of(known.getKey()));
            }
        }
        return bans.lookup(input);
    }

    private Error unknown(String player) {
        return Error.notFound(
                "Players.Unknown",
                Text.key("controlcore.errors.bans.unknown_player", "No Hytale player is called {player}.")
                        .with("player", player == null ? "" : player.strip()));
    }

    private static Error adminsOnly() {
        return Error.forbidden(
                "Players.AdminsOnly",
                Text.key(
                        "controlcore.errors.players.admins_only",
                        "Only admins (operators) can change operators and groups."));
    }

    public Result<String> kick(Optional<PlayerId> who, PlayerId target, String reason, Translator tr) {
        if (!may(who, KICK)) {
            return Result.failure(forbidden());
        }
        if (who.filter(target::equals).isPresent()) {
            return Result.failure(self());
        }
        if (players.find(target).isEmpty()) {
            return Result.failure(notOnline(target));
        }
        String name = nameOf(target);
        String shown = reason == null || reason.isBlank()
                ? texts.forPlayer(Optional.of(target)).t("controlcore.players.kick.default_reason")
                : reason.strip();
        if (!admin.kick(target, shown)) {
            return Result.failure(failed());
        }
        history.record(Kind.PLAYER, KICK_TARGET + target.value() + ":" + name, "", shown, false);
        changed();
        return Result.success(tr.t("controlcore.players.kicked", "player", name));
    }

    public CompletableFuture<Result<String>> toggleGameMode(Optional<PlayerId> who, PlayerId target, Translator tr) {
        if (!may(who, GAME_MODE)) {
            return CompletableFuture.completedFuture(Result.failure(forbidden()));
        }
        if (players.find(target).isEmpty()) {
            return CompletableFuture.completedFuture(Result.failure(notOnline(target)));
        }
        ChangeContext.Attribution attribution = ChangeContext.current();
        return admin.gameMode(target).thenCompose(current -> {
            String from = current.orElse(PlayerAdmin.ADVENTURE);
            String to = PlayerAdmin.CREATIVE.equals(from) ? PlayerAdmin.ADVENTURE : PlayerAdmin.CREATIVE;
            return admin.setGameMode(target, to)
                    .thenApply(ok -> ChangeContext.as(attribution, () -> {
                        if (!ok) {
                            return Result.<String>failure(failed());
                        }
                        String name = nameOf(target);
                        history.record(Kind.PLAYER, GAME_MODE_TARGET + target.value() + ":" + name, from, to, false);
                        Translator theirs = texts.forPlayer(Optional.of(target));
                        toast(
                                target,
                                theirs.t(
                                        "controlcore.players.toast.gamemode",
                                        "actor",
                                        actor(who, theirs),
                                        "mode",
                                        modeName(to, theirs)));
                        changed();
                        return Result.success(
                                tr.t("controlcore.players.gamemode_set", "player", name, "mode", modeName(to, tr)));
                    }));
        });
    }

    public CompletableFuture<Result<String>> teleport(
            Optional<PlayerId> who, PlayerId mover, PlayerId to, boolean bring, Translator tr) {
        if (!may(who, TELEPORT)) {
            return CompletableFuture.completedFuture(Result.failure(forbidden()));
        }
        if (who.isEmpty()) {
            return CompletableFuture.completedFuture(Result.failure(Error.validation(
                    "Players.PlayersOnly",
                    Text.key("controlcore.errors.players.players_only", "Only players in the game can do that."))));
        }
        if (mover.equals(to)) {
            return CompletableFuture.completedFuture(Result.failure(self()));
        }
        for (PlayerId needed : List.of(mover, to)) {
            if (players.find(needed).isEmpty()) {
                return CompletableFuture.completedFuture(Result.failure(notOnline(needed)));
            }
        }
        PlayerId other = bring ? mover : to;
        return admin.teleport(mover, to).thenApply(ok -> {
            if (!ok) {
                return Result.<String>failure(failed());
            }
            if (bring) {
                Translator theirs = texts.forPlayer(Optional.of(mover));
                toast(mover, theirs.t("controlcore.players.toast.brought", "actor", actor(who, theirs)));
            }
            return Result.success(tr.t(
                    bring ? "controlcore.players.brought" : "controlcore.players.teleported", "player", nameOf(other)));
        });
    }

    public Result<?> restore(String target, String old, String current, Optional<PlayerId> who) {
        boolean wanted = Boolean.parseBoolean(old);
        if (target.startsWith(WHITELIST_TARGET)) {
            Optional<PlayerId> id = idOf(target, WHITELIST_TARGET);
            return id.isEmpty()
                    ? Result.failure(failed())
                    : setWhitelisted(who, id.get().value().toString(), wanted, texts.forPlayer(who))
                            .join();
        }
        if (target.startsWith(GROUP_TARGET)) {
            Optional<PlayerId> id = idOf(target, GROUP_TARGET);
            int split = target.indexOf(SEPARATOR);
            return id.isEmpty() || split < 0
                    ? Result.failure(failed())
                    : setGroup(
                                    who,
                                    id.get().value().toString(),
                                    target.substring(split + 1),
                                    wanted,
                                    texts.forPlayer(who))
                            .join();
        }
        if (target.startsWith(GROUP_PERM_TARGET)) {
            String rest = target.substring(GROUP_PERM_TARGET.length());
            int split = rest.indexOf(SEPARATOR);
            return split < 0
                    ? Result.failure(failed())
                    : setGroupPermission(
                            who, rest.substring(0, split), rest.substring(split + 1), wanted, texts.forPlayer(who));
        }
        if (target.startsWith(KICK_TARGET)) {
            return Result.failure(Error.validation(
                    "Players.KickUndo", Text.key("controlcore.errors.players.kick_undo", "A kick can't be undone.")));
        }
        Optional<PlayerId> id = idOf(target, GAME_MODE_TARGET);
        if (id.isEmpty() || !(PlayerAdmin.ADVENTURE.equals(old) || PlayerAdmin.CREATIVE.equals(old))) {
            return Result.failure(failed());
        }
        if (!may(who, GAME_MODE)) {
            return Result.failure(forbidden());
        }
        if (players.find(id.get()).isEmpty()) {
            return Result.failure(notOnline(id.get()));
        }
        admin.setGameMode(id.get(), old);
        history.record(Kind.PLAYER, target, current, old, false);
        changed();
        return Result.success(true);
    }

    public String what(String target, Translator tr) {
        if (target.startsWith(GROUP_PERM_TARGET)) {
            String rest = target.substring(GROUP_PERM_TARGET.length());
            int split = rest.indexOf(SEPARATOR);
            return tr.t(
                    "controlcore.history.what.group_node",
                    "group",
                    groupName(split < 0 ? rest : rest.substring(0, split)),
                    "node",
                    split < 0 ? "" : rest.substring(split + 1));
        }
        for (String[] kind : new String[][] {
            {WHITELIST_TARGET, "controlcore.history.what.player_whitelist"},
            {GROUP_TARGET, "controlcore.history.what.player_group"}
        }) {
            if (target.startsWith(kind[0])) {
                String rest = target.substring(kind[0].length());
                int split = rest.indexOf(SEPARATOR);
                String group = split < 0 ? "" : rest.substring(split + 1);
                String person = split < 0 ? rest : rest.substring(0, split);
                int colon = person.indexOf(':');
                return tr.t(
                        kind[1], "player", colon < 0 ? person : person.substring(colon + 1), "group", groupName(group));
            }
        }
        boolean kick = target.startsWith(KICK_TARGET);
        String rest = target.substring((kick ? KICK_TARGET : GAME_MODE_TARGET).length());
        int colon = rest.indexOf(':');
        String name = colon < 0 ? rest : rest.substring(colon + 1);
        return tr.t(
                kick ? "controlcore.history.what.player_kick" : "controlcore.history.what.player_gamemode",
                "player",
                name.isEmpty() ? rest : name);
    }

    public String shown(String value, Translator tr) {
        if (PlayerAdmin.ADVENTURE.equals(value) || PlayerAdmin.CREATIVE.equals(value)) {
            return modeName(value, tr);
        }
        if ("true".equals(value) || "false".equals(value)) {
            return tr.t(Boolean.parseBoolean(value) ? "controlcore.players.value.yes" : "controlcore.players.value.no");
        }
        return value.isEmpty() ? tr.t("controlcore.history.value.empty") : value;
    }

    public PlayersView viewedBy(PlayerId viewer) {
        Translator tr = texts.forPlayer(Optional.of(viewer));
        Optional<PlayerId> who = Optional.of(viewer);
        PlayersView.Rights rights = new PlayersView.Rights(
                may(who, KICK),
                may(who, GAME_MODE),
                may(who, TELEPORT),
                bans.mayManage(who),
                may(who, WHITELIST),
                mayChangeGroups(who));
        return new PlayersView() {
            @Override
            public String viewer() {
                return viewer.value().toString();
            }

            @Override
            public Rights rights() {
                return rights;
            }

            @Override
            public List<Entry> list() {
                return PlayersService.this.list(tr);
            }

            @Override
            public List<Choice> filters() {
                return java.util.Arrays.stream(Filter.values())
                        .map(filter -> new Choice(filter.id(), tr.t("controlcore.players.filter." + filter.id())))
                        .toList();
            }

            @Override
            public List<Choice> lengths() {
                return bans.viewedBy(viewer).lengths().stream()
                        .map(length -> new Choice(length.id(), length.label()))
                        .toList();
            }

            @Override
            public boolean showHeads() {
                return bans.viewedBy(viewer).showHeads();
            }

            @Override
            public CompletableFuture<Optional<String>> resolve(String id) {
                return parse(id).map(bans::resolve).orElse(CompletableFuture.completedFuture(Optional.empty()));
            }

            @Override
            public CompletableFuture<Optional<String>> live(String id) {
                return parse(id)
                        .map(found -> PlayersService.this.live(found, tr))
                        .orElse(CompletableFuture.completedFuture(Optional.empty()));
            }

            @Override
            public CompletableFuture<Result<String>> ban(String player, String length, String reason) {
                return bans.ban(who, player, length, reason, tr);
            }

            @Override
            public Result<String> unban(String id) {
                return bans.unban(who, id, tr);
            }

            @Override
            public Result<String> kick(String id, String reason) {
                return parse(id)
                        .map(found -> PlayersService.this.kick(who, found, reason, tr))
                        .orElse(Result.failure(failed()));
            }

            @Override
            public CompletableFuture<Result<String>> toggleGameMode(String id) {
                return parse(id)
                        .map(found -> PlayersService.this.toggleGameMode(who, found, tr))
                        .orElse(CompletableFuture.completedFuture(Result.failure(failed())));
            }

            @Override
            public CompletableFuture<Result<String>> teleportTo(String id) {
                return parse(id)
                        .map(found -> teleport(who, viewer, found, false, tr))
                        .orElse(CompletableFuture.completedFuture(Result.failure(failed())));
            }

            @Override
            public CompletableFuture<Result<String>> bring(String id) {
                return parse(id)
                        .map(found -> teleport(who, found, viewer, true, tr))
                        .orElse(CompletableFuture.completedFuture(Result.failure(failed())));
            }

            @Override
            public List<Choice> groups() {
                return access.groups().stream()
                        .map(group -> new Choice(group, groupName(group)))
                        .toList();
            }

            @Override
            public List<String> groupPermissions(String group) {
                return List.copyOf(access.groupPermissions(group));
            }

            @Override
            public CompletableFuture<Result<String>> whitelist(String player, boolean add) {
                return setWhitelisted(who, player, add, tr);
            }

            @Override
            public CompletableFuture<Result<String>> operator(String player, boolean add) {
                return setGroup(who, player, AccessLists.OPERATOR_GROUP, add, tr);
            }

            @Override
            public CompletableFuture<Result<String>> group(String player, String group, boolean add) {
                return setGroup(who, player, group, add, tr);
            }

            @Override
            public Result<String> groupPermission(String group, String node, boolean add) {
                return setGroupPermission(who, group, node, add, tr);
            }

            @Override
            public Subscription onChange(Runnable listener) {
                listeners.add(listener);
                return () -> listeners.remove(listener);
            }
        };
    }

    private void toast(PlayerId player, String text) {
        try {
            notifications.toast(
                    player,
                    texts.forPlayer(Optional.of(player)).t("controlcore.players.toast.title"),
                    text,
                    ToastLevel.INFO,
                    TOAST_SECONDS);
        } catch (RuntimeException toastNotShown) {
            return;
        }
    }

    private String actor(Optional<PlayerId> who, Translator tr) {
        return who.map(this::nameOf).orElse(tr.t("controlcore.players.console"));
    }

    private String nameOf(PlayerId id) {
        return players.find(id)
                .map(PlayerInfo::name)
                .or(() -> data.get(id, SessionService.NAME))
                .or(() -> bans.knownName(id))
                .orElse(id.value().toString());
    }

    private static String modeName(String mode, Translator tr) {
        return tr.t("controlcore.players.mode." + mode);
    }

    private String playtime(PlayerId id) {
        Duration played = sessions.find(id).map(session -> session.playtime()).orElse(Duration.ZERO);
        long minutes = played.toMinutes();
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    private String when(Instant instant) {
        return WHEN.format(instant.atZone(ZoneId.systemDefault()));
    }

    private Optional<Instant> instant(PlayerId id, String key) {
        try {
            return data.get(id, key).map(Instant::parse);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<PlayerId> parse(String id) {
        try {
            return Optional.of(PlayerId.of(UUID.fromString(id)));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static Optional<PlayerId> idOf(String target, String prefix) {
        if (!target.startsWith(prefix)) {
            return Optional.empty();
        }
        String rest = target.substring(prefix.length());
        int colon = rest.indexOf(':');
        return parse(colon < 0 ? rest : rest.substring(0, colon));
    }

    private Error notOnline(PlayerId player) {
        return Error.validation(
                "Players.NotOnline",
                Text.key("controlcore.errors.players.not_online", "{player} is not online.")
                        .with("player", nameOf(player)));
    }

    private static Error self() {
        return Error.validation(
                "Players.Self", Text.key("controlcore.errors.players.self", "You can't do that to yourself."));
    }

    private static Error failed() {
        return Error.failure(
                "Players.Failed",
                Text.key("controlcore.errors.players.failed", "That didn't work. The server log has the details."));
    }

    private static Error forbidden() {
        return Error.forbidden(
                "Players.Forbidden",
                Text.key("controlcore.errors.players.forbidden", "You are not allowed to do that."));
    }
}
