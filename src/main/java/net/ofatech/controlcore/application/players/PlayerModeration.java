package net.ofatech.controlcore.application.players;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.application.worlds.WorldsService;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.ChatGate;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin.Spot;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class PlayerModeration {

    static final String MUTED_UNTIL = "players.muted-until";
    static final String FROZEN = "players.frozen";
    static final String PERMANENT = "permanent";
    static final int MAX_REASON = 200;
    private static final Duration HOLD_EVERY = Duration.ofSeconds(1);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TOAST_SECONDS = 6;

    private final PlayerDirectory directory;
    private final PlayerGuard guard;
    private final PlayerData data;
    private final PlayerAdmin admin;
    private final WorldsService worlds;
    private final ChangeHistory history;
    private final Texts texts;
    private final NotificationService notifications;
    private final Clock clock;
    private final Scheduler scheduler;
    private final PlayersChanges changes;
    private final Log log;
    private final Map<PlayerId, Spot> held = new ConcurrentHashMap<>();
    private final AtomicBoolean holding = new AtomicBoolean();

    public PlayerModeration(
            PlayerDirectory directory,
            PlayerGuard guard,
            PlayerData data,
            PlayerAdmin admin,
            WorldsService worlds,
            ChatGate chatGate,
            RecentChat chat,
            ChangeHistory history,
            Texts texts,
            NotificationService notifications,
            Clock clock,
            Scheduler scheduler,
            PlayersChanges changes,
            Log log) {
        this.directory = directory;
        this.guard = guard;
        this.data = data;
        this.admin = admin;
        this.worlds = worlds;
        this.history = history;
        this.texts = texts;
        this.notifications = notifications;
        this.clock = clock;
        this.scheduler = scheduler;
        this.changes = changes;
        this.log = log;
        chatGate.listen(this::allowChat);
        chatGate.observe(chat::add);
    }

    boolean allowChat(PlayerId sender, String name, String text) {
        Optional<String> muted = activeMute(sender);
        if (muted.isEmpty()) {
            if (data.get(sender, MUTED_UNTIL).isPresent()) {
                writeMute(sender, "");
            }
            return true;
        }
        try {
            Translator theirs = texts.forPlayer(Optional.of(sender));
            notifications.message(
                    sender,
                    PERMANENT.equals(muted.get())
                            ? theirs.t("controlcore.players.you_are_muted")
                            : theirs.t(
                                    "controlcore.players.you_are_muted_until",
                                    "when",
                                    when(Instant.parse(muted.get()))));
        } catch (RuntimeException e) {
            log.warn("Could not tell " + name + " they are muted: " + e);
        }
        return false;
    }

    Optional<String> activeMute(PlayerId id) {
        Optional<String> stored = data.get(id, MUTED_UNTIL).filter(value -> !value.isBlank());
        if (stored.isEmpty() || PERMANENT.equals(stored.get())) {
            return stored;
        }
        return stored.filter(this::inFuture);
    }

    boolean frozen(PlayerId id) {
        return data.get(id, FROZEN).isPresent();
    }

    public CompletableFuture<Result<String>> setMuted(
            Optional<PlayerId> who, String player, String length, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.MUTE)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.forbidden()));
        }
        Result<Optional<Duration>> duration = add ? BansService.duration(length) : Result.success(Optional.empty());
        if (duration.isFailure()) {
            return PlayerDirectory.completed(Result.failure(duration.error()));
        }
        return directory.resolve(player, id -> mute(who, id, duration.value(), add, tr));
    }

    Result<String> mute(Optional<PlayerId> who, PlayerId id, Optional<Duration> length, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.MUTE)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(id);
        Optional<Error> refused =
                PlayerGuard.self(who, id).or(() -> add ? guard.protects(who, id, name) : Optional.empty());
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        String before = activeMute(id).orElse("");
        if (!add && before.isEmpty()) {
            return Result.failure(PlayerErrors.nothingToDo(
                    "controlcore.errors.players.not_muted", "{player} is not muted.", "player", name));
        }
        String after =
                add ? length.map(time -> clock.now().plus(time).toString()).orElse(PERMANENT) : "";
        if (add && after.equals(before)) {
            return Result.failure(PlayerErrors.nothingToDo(
                    "controlcore.errors.players.already_muted",
                    "{player} is already muted ({length}).",
                    "player",
                    name,
                    "length",
                    shownMute(before, tr)));
        }
        writeMute(id, after);
        history.record(Kind.PLAYER, PlayerChange.MUTE.target(id, name), before, after, false);
        toastMute(who, id, after);
        changes.fire();
        if (add && !before.isEmpty()) {
            return Result.success(tr.t(
                    "controlcore.players.mute_changed",
                    "player",
                    name,
                    "before",
                    shownMute(before, tr),
                    "length",
                    shownMute(after, tr)));
        }
        return Result.success(tr.t(
                add ? "controlcore.players.muted" : "controlcore.players.unmuted",
                "player",
                name,
                "length",
                shownMute(after, tr)));
    }

    Result<?> restoreMute(Optional<PlayerId> who, PlayerId id, String old, String current) {
        if (!guard.may(who, PlayerGuard.MUTE)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(id);
        String wanted = PERMANENT.equals(old) || inFuture(old) ? old : "";
        Optional<Error> refused =
                PlayerGuard.self(who, id).or(() -> wanted.isEmpty() ? Optional.empty() : guard.protects(who, id, name));
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        writeMute(id, wanted);
        history.record(Kind.PLAYER, PlayerChange.MUTE.target(id, name), current, wanted, false);
        toastMute(who, id, wanted);
        changes.fire();
        return Result.success(true);
    }

    public CompletableFuture<Result<String>> setFrozen(
            Optional<PlayerId> who, String player, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.FREEZE)) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.forbidden()));
        }
        return directory.resolve(player, id -> freeze(who, id, add, tr));
    }

    Result<String> freeze(Optional<PlayerId> who, PlayerId id, boolean add, Translator tr) {
        if (!guard.may(who, PlayerGuard.FREEZE)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(id);
        if (add && !directory.online(id)) {
            return Result.failure(PlayerErrors.notOnline(name));
        }
        Optional<Error> refused =
                PlayerGuard.self(who, id).or(() -> add ? guard.protects(who, id, name) : Optional.empty());
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        if (frozen(id) == add) {
            return Result.failure(PlayerErrors.nothingToDo(
                    add ? "controlcore.errors.players.already_frozen" : "controlcore.errors.players.not_frozen",
                    add ? "{player} is already frozen." : "{player} is not frozen.",
                    "player",
                    name));
        }
        if (add) {
            data.put(id, FROZEN, "true");
            hold(id);
        } else {
            data.remove(id, FROZEN);
            held.remove(id);
        }
        data.flush();
        worlds.setFrozen(id, add);
        history.record(
                Kind.PLAYER, PlayerChange.FREEZE.target(id, name), String.valueOf(!add), String.valueOf(add), false);
        if (directory.online(id)) {
            Translator theirs = texts.forPlayer(Optional.of(id));
            toast(
                    id,
                    theirs.t(
                            add ? "controlcore.players.toast.frozen" : "controlcore.players.toast.unfrozen",
                            "actor",
                            actor(who, theirs)));
        }
        changes.fire();
        return Result.success(
                tr.t(add ? "controlcore.players.frozen" : "controlcore.players.unfrozen", "player", name));
    }

    public Result<String> kick(Optional<PlayerId> who, PlayerId target, String reason, Translator tr) {
        if (!guard.may(who, PlayerGuard.KICK)) {
            return Result.failure(PlayerErrors.forbidden());
        }
        String name = directory.nameOf(target);
        Optional<Error> refused = PlayerGuard.self(who, target).or(() -> guard.protects(who, target, name));
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        if (!directory.online(target)) {
            return Result.failure(PlayerErrors.notOnline(name));
        }
        String cleaned = RecentChat.oneLine(reason, MAX_REASON);
        String shown = cleaned.isEmpty()
                ? texts.forPlayer(Optional.of(target)).t("controlcore.players.kick.default_reason")
                : cleaned;
        if (!admin.kick(target, shown)) {
            return Result.failure(PlayerErrors.failed());
        }
        history.record(Kind.PLAYER, PlayerChange.KICK.target(target, name), "", shown, false);
        changes.fire();
        return Result.success(tr.t("controlcore.players.kicked", "player", name));
    }

    Result<String> kickNamed(Optional<PlayerId> who, String player, String reason, Translator tr) {
        String input = PlayerDirectory.clean(player);
        if (input.isEmpty()) {
            return Result.failure(PlayerErrors.noPlayer());
        }
        return directory
                .onlineNamed(input)
                .map(id -> kick(who, id, reason, tr))
                .orElseGet(() -> Result.failure(PlayerErrors.notOnline(input)));
    }

    public void joined(PlayerId id) {
        if (!frozen(id)) {
            return;
        }
        worlds.setFrozen(id, true);
        scheduler.runLater(SETTLE, () -> hold(id));
    }

    public void left(PlayerId id) {
        held.remove(id);
    }

    private void hold(PlayerId id) {
        admin.spot(id)
                .thenAccept(spot -> spot.ifPresent(found -> {
                    if (frozen(id)) {
                        held.put(id, found);
                    }
                }));
        if (holding.compareAndSet(false, true)) {
            scheduler.schedule(HOLD_EVERY, HOLD_EVERY, this::holdFrozen);
        }
    }

    void holdFrozen() {
        held.forEach((id, spot) -> {
            if (directory.online(id) && frozen(id)) {
                admin.keepAt(id, spot);
            }
        });
    }

    private void writeMute(PlayerId id, String value) {
        if (value == null || value.isBlank()) {
            data.remove(id, MUTED_UNTIL);
        } else {
            data.put(id, MUTED_UNTIL, value);
        }
        data.flush();
    }

    private void toastMute(Optional<PlayerId> who, PlayerId id, String after) {
        if (!directory.online(id)) {
            return;
        }
        Translator theirs = texts.forPlayer(Optional.of(id));
        toast(
                id,
                after.isEmpty()
                        ? theirs.t("controlcore.players.toast.unmuted", "actor", actor(who, theirs))
                        : theirs.t(
                                "controlcore.players.toast.muted",
                                "actor",
                                actor(who, theirs),
                                "length",
                                shownMute(after, theirs)));
    }

    String shownMute(String value, Translator tr) {
        if (value == null || value.isEmpty()) {
            return tr.t("controlcore.players.not_muted");
        }
        if (PERMANENT.equals(value)) {
            return tr.t("controlcore.players.muted_forever");
        }
        try {
            return tr.t("controlcore.players.muted_until", "when", when(Instant.parse(value)));
        } catch (DateTimeParseException broken) {
            return value;
        }
    }

    private boolean inFuture(String value) {
        try {
            return Instant.parse(value).isAfter(clock.now());
        } catch (DateTimeParseException broken) {
            return false;
        }
    }

    void toast(PlayerId player, String text) {
        try {
            notifications.toast(
                    player,
                    texts.forPlayer(Optional.of(player)).t("controlcore.players.toast.title"),
                    text,
                    ToastLevel.INFO,
                    TOAST_SECONDS);
        } catch (RuntimeException e) {
            log.warn("Could not show a toast to " + directory.nameOf(player) + ": " + e);
        }
    }

    String actor(Optional<PlayerId> who, Translator tr) {
        return who.map(directory::nameOf).orElse(tr.t("controlcore.players.console"));
    }

    static String when(Instant instant) {
        return WHEN.format(instant.atZone(ZoneId.systemDefault()));
    }
}
