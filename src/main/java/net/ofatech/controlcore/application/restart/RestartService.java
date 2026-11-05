package net.ofatech.controlcore.application.restart;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ofatech.controlcore.application.automation.AutomationService;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.backups.BackupService;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.CronExpression;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Source;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.server.RestartView;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.ServerControl;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class RestartService {

    public static final String NODE = "restart";
    public static final List<String> DELAYS = List.of("default", "10s", "1m", "5m", "10m");
    static final List<Integer> STEPS = List.of(600, 300, 60, 30, 10);
    static final Duration MIN = Duration.ofSeconds(10);
    static final Duration MAX = Duration.ofHours(24);
    static final Duration REFRESH = Duration.ofSeconds(10);
    static final Duration STATUS_REFRESH = Duration.ofMinutes(1);
    static final Duration BACKUP_LIMIT = Duration.ofMinutes(10);
    static final String BAR = "controlcore-restart";
    static final int MAX_REASON = 256;

    private static final Pattern DELAY = Pattern.compile("(\\d{1,6})\\s*([smh]?)");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ROOT);

    public interface Listener {
        void started(Duration delay, String reason);

        void stopping();
    }

    private final RestartSettings settings;
    private final BackupService backups;
    private final ServerControl control;
    private final Players players;
    private final PlayerAdmin admin;
    private final NotificationService notifications;
    private final Messenger messenger;
    private final Permissions permissions;
    private final SettingPermissionPolicy policy;
    private final Clock clock;
    private final Texts texts;
    private final Log log;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> watchers = new CopyOnWriteArrayList<>();
    private final Set<LocalDateTime> scheduledDone = new HashSet<>();
    private final Set<Integer> warned = new HashSet<>();
    private Instant at;
    private String reason = "";
    private Instant shown;
    private boolean finishing;
    private boolean stopStarted;
    private boolean stopFired;
    private Instant backupUntil;
    private Instant waitUntil;
    private long attempt;
    private volatile Instant refreshed;

    public RestartService(
            RestartSettings settings,
            BackupService backups,
            ServerControl control,
            Players players,
            PlayerAdmin admin,
            NotificationService notifications,
            Messenger messenger,
            Permissions permissions,
            SettingPermissionPolicy policy,
            Clock clock,
            Texts texts,
            Log log) {
        this.settings = settings;
        this.backups = backups;
        this.control = control;
        this.players = players;
        this.admin = admin;
        this.notifications = notifications;
        this.messenger = messenger;
        this.permissions = permissions;
        this.policy = policy;
        this.clock = clock;
        this.texts = texts;
        this.log = log;
        control.onStopping(this::stopping);
    }

    public void listen(Listener listener) {
        listeners.add(listener);
    }

    public boolean mayManage(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), NODE);
    }

    public synchronized boolean counting() {
        return at != null || finishing;
    }

    public synchronized Optional<Duration> remaining() {
        if (at == null) {
            return finishing ? Optional.of(Duration.ZERO) : Optional.empty();
        }
        Duration left = Duration.between(clock.now(), at);
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }

    public synchronized String reason() {
        return reason;
    }

    public synchronized Result<String> start(
            Optional<Duration> delay, String why, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        if (counting()) {
            return Result.failure(Error.conflict(
                    "Restart.Running",
                    Text.key(
                            "controlcore.errors.restart.running",
                            "A restart is already counting down. Cancel it first.")));
        }
        Duration length = delay.orElseGet(settings::countdown);
        if (length.compareTo(MIN) < 0 || length.compareTo(MAX) > 0) {
            return Result.failure(badDelay(length.toSeconds() + "s"));
        }
        String text = why == null ? "" : why.strip();
        if (text.length() > MAX_REASON) {
            return Result.failure(Error.validation(
                    "Restart.InvalidReason",
                    Text.key("controlcore.errors.restart.reason", "Keep the reason under 256 characters.")));
        }
        Instant now = clock.now();
        at = now.plus(length);
        waitUntil = null;
        reason = text;
        warned.clear();
        long seconds = length.toSeconds();
        STEPS.stream().filter(step -> step >= seconds).forEach(warned::add);
        announce(true);
        log.info("Restart in " + clockText(length) + (text.isEmpty() ? "" : ": " + text) + " (by "
                + ChangeContext.current().actor().name() + ")");
        for (Listener listener : listeners) {
            try {
                listener.started(length, text);
            } catch (RuntimeException e) {
                log.error("A restart listener failed", e);
            }
        }
        changed();
        return Result.success(tr.t("controlcore.restart.started", "time", clockText(length)));
    }

    public synchronized Result<String> cancel(Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        if (finishing) {
            return Result.failure(Error.conflict(
                    "Restart.TooLate",
                    Text.key("controlcore.errors.restart.too_late", "The server is already stopping.")));
        }
        if (at == null) {
            return Result.failure(Error.conflict(
                    "Restart.NotRunning",
                    Text.key("controlcore.errors.restart.not_running", "No restart is counting down.")));
        }
        at = null;
        waitUntil = null;
        reason = "";
        notifications.clearWarningAll(BAR);
        for (PlayerInfo player : players.online()) {
            messenger.send(
                    player.id(), texts.forPlayer(Optional.of(player.id())).t("controlcore.restart.cancelled"));
        }
        log.info("Restart cancelled by " + ChangeContext.current().actor().name());
        changed();
        return Result.success(tr.t("controlcore.restart.cancelled"));
    }

    public synchronized void tick() {
        Instant now = clock.now();
        if (counting() && (refreshed == null || !now.isBefore(refreshed.plus(STATUS_REFRESH)))) {
            changed();
        }
        if (finishing) {
            if (backupUntil != null && !now.isBefore(backupUntil)) {
                backupTimedOut();
                proceed(attempt);
            }
            return;
        }
        if (at == null) {
            checkSchedule(now);
            return;
        }
        Duration left = Duration.between(now, at);
        if (!left.isPositive()) {
            if (backups.running()) {
                if (waitUntil == null) {
                    waitUntil = now.plus(BACKUP_LIMIT);
                }
                if (now.isBefore(waitUntil)) {
                    showAll("controlcore.restart.waiting_backup", false);
                    return;
                }
                backupTimedOut();
                waitUntil = null;
                finish(false);
                return;
            }
            waitUntil = null;
            finish(true);
            return;
        }
        long seconds = (left.toMillis() + 999) / 1000;
        Optional<Integer> step = STEPS.stream()
                .filter(candidate -> seconds <= candidate && !warned.contains(candidate))
                .findFirst();
        if (step.isPresent()) {
            STEPS.stream().filter(candidate -> seconds <= candidate).forEach(warned::add);
            announce(true);
            changed();
        } else if (shown == null || !now.isBefore(shown.plus(REFRESH))) {
            announce(false);
            changed();
        }
    }

    private void announce(boolean chat) {
        shown = clock.now();
        Duration left = Duration.between(shown, at);
        for (PlayerInfo player : players.online()) {
            Translator tr = texts.forPlayer(Optional.of(player.id()));
            String text = warning(left, tr);
            notifications.warning(player.id(), BAR, text, ToastLevel.WARNING, 0);
            if (chat) {
                messenger.send(player.id(), text);
            }
        }
    }

    private String warning(Duration left, Translator tr) {
        return reason.isEmpty()
                ? tr.t("controlcore.restart.warning", "time", clockText(left))
                : tr.t("controlcore.restart.warning_reason", "time", clockText(left), "reason", reason);
    }

    private void showAll(String key, boolean chat) {
        for (PlayerInfo player : players.online()) {
            String text = texts.forPlayer(Optional.of(player.id())).t(key);
            notifications.warning(player.id(), BAR, text, ToastLevel.WARNING, 0);
            if (chat) {
                messenger.send(player.id(), text);
            }
        }
    }

    private void finish(boolean backUp) {
        at = null;
        finishing = true;
        stopStarted = false;
        long mine = ++attempt;
        changed();
        boolean wanted = backUp && settings.backupFirst() && backups.configured();
        if (wanted && backups.pending().isPresent()) {
            log.info("Not backing up before the restart: a backup restore is planned for the next start");
            toAdmins("controlcore.restart.backup_skipped_restore");
            wanted = false;
        }
        if (wanted) {
            showAll("controlcore.restart.backing_up", true);
            backupUntil = clock.now().plus(BACKUP_LIMIT);
            var made = ChangeContext.as(
                    new ChangeContext.Attribution(
                            Actor.console(texts.forServer().t("controlcore.restart.actor")), Source.AUTOMATION, 0),
                    () -> backups.create(Optional.empty(), texts.forServer()));
            backups.tellAdminsIfFailed(made, "controlcore.restart.backup_failed");
            made.whenComplete((result, failure) -> proceed(mine));
            return;
        }
        proceed(mine);
    }

    private void backupTimedOut() {
        log.warn("The backup before restarting did not finish within " + BACKUP_LIMIT.toMinutes()
                + " minutes, restarting anyway");
        toAdmins("controlcore.restart.backup_timeout", "minutes", BACKUP_LIMIT.toMinutes());
    }

    private void toAdmins(String key, Object... nameValuePairs) {
        notifications.toEachAdmin(player -> texts.forPlayer(Optional.of(player)).t(key, nameValuePairs));
    }

    private void proceed(long mine) {
        synchronized (this) {
            if (stopStarted || !finishing || mine != attempt) {
                return;
            }
            stopStarted = true;
            backupUntil = null;
        }
        try {
            stopNow();
        } catch (RuntimeException e) {
            log.error("Could not stop the server for the restart", e);
            synchronized (this) {
                finishing = false;
                stopStarted = false;
                stopFired = false;
            }
            toAdmins("controlcore.restart.stop_failed", "reason", String.valueOf(e.getMessage()));
            changed();
        }
    }

    private void stopNow() {
        stopping();
        for (PlayerInfo player : List.copyOf(players.online())) {
            admin.kick(player.id(), texts.forPlayer(Optional.of(player.id())).t("controlcore.restart.kick"));
        }
        log.info("Restarting: stopping the server");
        control.stop(texts.forServer().t("controlcore.restart.kick"));
    }

    void stopping() {
        synchronized (this) {
            if (stopFired) {
                return;
            }
            stopFired = true;
        }
        for (Listener listener : listeners) {
            try {
                listener.stopping();
            } catch (RuntimeException e) {
                log.error("A server stopping listener failed", e);
            }
        }
    }

    private void checkSchedule(Instant now) {
        List<CronExpression> entries = schedule(settings.schedule()).valid();
        if (entries.isEmpty()) {
            return;
        }
        Duration countdown = settings.countdown();
        Optional<LocalDateTime> hit = due(entries, now, countdown, scheduledDone);
        if (hit.isEmpty()) {
            return;
        }
        scheduledDone.add(hit.get());
        Instant target = hit.get().atZone(ZoneId.systemDefault()).toInstant();
        Duration delay = Duration.between(now, target);
        Translator tr = texts.forServer();
        Result<String> started = ChangeContext.as(
                new ChangeContext.Attribution(
                        Actor.console(tr.t("controlcore.restart.scheduled_actor")), Source.AUTOMATION, 0),
                () -> start(
                        Optional.of(delay.compareTo(MIN) < 0 ? MIN : delay),
                        tr.t("controlcore.restart.scheduled_reason"),
                        Optional.empty(),
                        tr));
        if (started.isFailure()) {
            log.warn("Could not start the scheduled restart: " + tr.error(started.error()));
        }
    }

    record Schedule(List<CronExpression> valid, List<String> invalid) {}

    static Schedule schedule(String text) {
        List<CronExpression> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String part : (text == null ? "" : text).split(";")) {
            String entry = part.strip();
            if (entry.isEmpty()) {
                continue;
            }
            Optional<CronExpression> cron = AutomationService.clockTime(entry)
                    .map(time -> CronExpression.parse(time.getMinute() + " " + time.getHour() + " * * *"))
                    .orElseGet(() -> CronExpression.parse(entry));
            cron.ifPresentOrElse(valid::add, () -> invalid.add(entry));
        }
        return new Schedule(valid, invalid);
    }

    static Optional<LocalDateTime> due(
            List<CronExpression> entries, Instant now, Duration countdown, Set<LocalDateTime> done) {
        LocalDateTime until = LocalDateTime.ofInstant(now.plus(countdown), ZoneId.systemDefault());
        for (LocalDateTime minute = LocalDateTime.ofInstant(now, ZoneId.systemDefault())
                        .truncatedTo(ChronoUnit.MINUTES)
                        .plusMinutes(1);
                !minute.isAfter(until);
                minute = minute.plusMinutes(1)) {
            LocalDateTime candidate = minute;
            if (!done.contains(candidate) && entries.stream().anyMatch(cron -> cron.matches(candidate))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<LocalDateTime> nextScheduled() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.now(), ZoneId.systemDefault());
        return schedule(settings.schedule()).valid().stream()
                .map(cron -> cron.next(now))
                .flatMap(Optional::stream)
                .min(LocalDateTime::compareTo);
    }

    public String status(Translator tr) {
        Optional<Duration> left = remaining();
        String line = left.isEmpty()
                ? tr.t("controlcore.restart.idle")
                : finishing()
                        ? tr.t("controlcore.restart.stopping")
                        : tr.t("controlcore.restart.counting", "time", clockText(left.get()));
        Schedule schedule = schedule(settings.schedule());
        Optional<LocalDateTime> next = nextScheduled();
        if (next.isPresent()) {
            line += " " + tr.t("controlcore.restart.next", "when", WHEN.format(next.get()));
        }
        if (!schedule.invalid().isEmpty()) {
            line += " "
                    + tr.t("controlcore.restart.invalid_schedule", "entries", String.join("; ", schedule.invalid()));
        }
        return line;
    }

    private synchronized boolean finishing() {
        return finishing;
    }

    public static String clockText(Duration duration) {
        long seconds = Math.max(0, (duration.toMillis() + 999) / 1000);
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    public static Result<Optional<Duration>> delay(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("default")) {
            return Result.success(Optional.empty());
        }
        Matcher matcher = DELAY.matcher(value);
        if (!matcher.matches()) {
            return Result.failure(badDelay(value));
        }
        long amount = Long.parseLong(matcher.group(1));
        Duration length = switch (matcher.group(2)) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> Duration.ofSeconds(amount);
        };
        if (length.compareTo(MIN) < 0 || length.compareTo(MAX) > 0) {
            return Result.failure(badDelay(value));
        }
        return Result.success(Optional.of(length));
    }

    private void changed() {
        refreshed = clock.now();
        for (Runnable watcher : watchers) {
            try {
                watcher.run();
            } catch (RuntimeException e) {
                log.error("A restart watcher failed", e);
            }
        }
    }

    public RestartView viewedBy(PlayerId viewer) {
        Optional<PlayerId> who = Optional.of(viewer);
        Translator tr = texts.forPlayer(who);
        return new RestartView() {
            @Override
            public boolean counting() {
                return RestartService.this.counting();
            }

            @Override
            public String status() {
                return RestartService.this.status(tr);
            }

            @Override
            public List<Choice> delays() {
                return DELAYS.stream()
                        .map(id -> new Choice(id, tr.t("controlcore.restart.delay." + id)))
                        .toList();
            }

            @Override
            public Result<String> restart(String delay) {
                return RestartService.delay(delay).flatMap(length -> start(length, "", who, tr));
            }

            @Override
            public Result<String> cancel() {
                return RestartService.this.cancel(who, tr);
            }

            @Override
            public Subscription onChange(Runnable listener) {
                watchers.add(listener);
                return () -> watchers.remove(listener);
            }
        };
    }

    static Error badDelay(String value) {
        return Error.validation(
                "Restart.BadDelay",
                Text.key(
                                "controlcore.errors.restart.delay",
                                "'{value}' is not a countdown. Use 10s to 24h, for example 30s, 5m or 1h.")
                        .with("value", value));
    }

    private static Error forbidden() {
        return Error.forbidden(
                "Restart.Forbidden",
                Text.key("controlcore.errors.restart.not_allowed", "You may not restart the server."));
    }
}
