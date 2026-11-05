package net.ofatech.controlcore.application.maintenance;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.CommonErrors;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Source;
import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.domain.model.server.MaintenanceView;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.MaintenanceStore;
import net.ofatech.controlcore.domain.ports.game.JoinGate;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class MaintenanceService {

    public static final String NODE = "maintenance";
    public static final String JOIN = "maintenance.join";
    public static final String TARGET = "maintenance";
    public static final List<String> OFF_AFTER = List.of("never", "15m", "30m", "1h", "2h", "4h");
    static final int MAX_TEXT = 256;

    private static final SettingId MOTD = ServerConfigEditor.idOf("MOTD");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TRY_LATER = "The server can't check who may join right now. Please try again later.";

    private final MaintenanceStore store;
    private final Players players;
    private final PlayerAdmin admin;
    private final Permissions permissions;
    private final SettingPermissionPolicy policy;
    private final ServerConfigEditor serverConfig;
    private final ChangeHistory history;
    private final Clock clock;
    private final Texts texts;
    private final Log log;
    private MaintenanceState state;

    public MaintenanceService(
            MaintenanceStore store,
            JoinGate gate,
            Players players,
            PlayerAdmin admin,
            Permissions permissions,
            SettingPermissionPolicy policy,
            ServerConfigEditor serverConfig,
            ChangeHistory history,
            Clock clock,
            Texts texts,
            Log log) {
        this.store = store;
        this.players = players;
        this.admin = admin;
        this.permissions = permissions;
        this.policy = policy;
        this.serverConfig = serverConfig;
        this.history = history;
        this.clock = clock;
        this.texts = texts;
        this.log = log;
        gate.listen(this::refuse);
    }

    public boolean mayManage(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), NODE);
    }

    public boolean mayJoin(PlayerId player) {
        return policy.isAdmin(player) || permissions.hasOwn(player, JOIN) || permissions.hasOwn(player, NODE);
    }

    public synchronized MaintenanceState state() {
        if (state == null) {
            state = store.load();
        }
        return state;
    }

    Optional<String> refuse(PlayerId player, String name) {
        try {
            MaintenanceState now = state();
            if (!now.on()) {
                return Optional.empty();
            }
            return mayJoin(player)
                    ? Optional.empty()
                    : Optional.of(messageFor(now, texts.forPlayer(Optional.of(player))));
        } catch (RuntimeException e) {
            if (isAdmin(player)) {
                log.error("The maintenance join check failed for " + name + "; letting the admin in", e);
                return Optional.empty();
            }
            log.error("The maintenance join check failed for " + name + "; refusing the join", e);
            return Optional.of(tryLater());
        }
    }

    private boolean isAdmin(PlayerId player) {
        try {
            return policy.isAdmin(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String tryLater() {
        try {
            return texts.forServer().t("controlcore.maintenance.try_later");
        } catch (RuntimeException e) {
            return TRY_LATER;
        }
    }

    public static String messageFor(MaintenanceState state, Translator tr) {
        return state.message().isBlank() ? tr.t("controlcore.maintenance.default_message") : state.message();
    }

    public synchronized Result<String> turnOn(
            String message, String motd, Optional<Duration> offAfter, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        String text = clean(message);
        boolean motdIgnored =
                who.isPresent() && !policy.isAdmin(who.get()) && !clean(motd).isEmpty();
        String newMotd = motdIgnored ? "" : clean(motd);
        if (text.length() > MAX_TEXT || newMotd.length() > MAX_TEXT) {
            return Result.failure(Error.validation(
                    "Maintenance.InvalidText",
                    Text.key(
                            "controlcore.errors.maintenance.invalid_text",
                            "Keep the message and MOTD under 256 characters.")));
        }
        MaintenanceState now = state();
        if (now.on()) {
            return Result.failure(Error.conflict(
                    "Maintenance.AlreadyOn",
                    Text.key("controlcore.errors.maintenance.already_on", "Maintenance mode is already on.")));
        }
        Optional<String> saved = now.savedMotd();
        boolean writeMotd = false;
        if (!newMotd.isEmpty()) {
            Result<SettingSnapshot> current = serverConfig.read(MOTD, Optional.empty());
            if (current.isSuccess() && !current.value().value().equals(newMotd)) {
                writeMotd = true;
                saved = now.savedMotd().or(() -> Optional.of(current.value().value()));
            }
        }
        Instant at = clock.now();
        MaintenanceState next = new MaintenanceState(
                true,
                text,
                newMotd,
                Optional.of(at),
                offAfter.filter(time -> !time.isZero()).map(at::plus),
                saved);
        if (!store.save(next)) {
            return Result.failure(CommonErrors.notSaved());
        }
        if (writeMotd) {
            Result<SettingSnapshot> written = serverConfig.write(MOTD, Optional.empty(), newMotd);
            if (written.isFailure()) {
                store.save(now);
                return Result.failure(written.error());
            }
        }
        state = next;
        history.record(Kind.MAINTENANCE, TARGET, "off", "on", false);
        int kicked = 0;
        int kept = 0;
        for (PlayerInfo player : List.copyOf(players.online())) {
            if (mayJoin(player.id())) {
                kept++;
            } else if (admin.kick(player.id(), messageFor(next, texts.forPlayer(Optional.of(player.id()))))) {
                kicked++;
            }
        }
        log.info("Maintenance mode is on; " + kicked + " players kicked, " + kept + " kept");
        String done = tr.t("controlcore.maintenance.turned_on", "kicked", kicked, "kept", kept);
        return Result.success(motdIgnored ? done + " " + tr.t("controlcore.maintenance.motd_ignored") : done);
    }

    public synchronized Result<String> turnOff(Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        MaintenanceState now = state();
        if (!now.on()) {
            return Result.failure(Error.conflict(
                    "Maintenance.AlreadyOff",
                    Text.key("controlcore.errors.maintenance.already_off", "Maintenance mode is already off.")));
        }
        MaintenanceState next = new MaintenanceState(
                false, now.message(), now.motd(), Optional.empty(), Optional.empty(), now.savedMotd());
        if (!store.save(next)) {
            return Result.failure(CommonErrors.notSaved());
        }
        state = next;
        restoreMotd(next);
        history.record(Kind.MAINTENANCE, TARGET, "on", "off", false);
        log.info("Maintenance mode is off");
        return Result.success(tr.t("controlcore.maintenance.turned_off"));
    }

    public synchronized void tick() {
        MaintenanceState now = state();
        if (now.on()
                && now.until().filter(until -> !clock.now().isBefore(until)).isPresent()) {
            Translator tr = texts.forServer();
            Result<String> done = ChangeContext.as(
                    new ChangeContext.Attribution(
                            Actor.console(tr.t("controlcore.maintenance.timer")), Source.AUTOMATION, 0),
                    () -> turnOff(Optional.empty(), tr));
            if (done.isFailure()) {
                log.warn("Could not turn maintenance mode off on time: "
                        + done.error().description());
            }
        }
    }

    public synchronized void restoreOnStart() {
        MaintenanceState now = state();
        if (!now.on() && now.savedMotd().isPresent()) {
            restoreMotd(now);
        }
    }

    public Result<?> rollback(String old, Optional<PlayerId> who) {
        Translator tr = texts.forPlayer(who);
        MaintenanceState now = state();
        return "on".equals(old) ? turnOn(now.message(), now.motd(), Optional.empty(), who, tr) : turnOff(who, tr);
    }

    private void restoreMotd(MaintenanceState now) {
        if (now.savedMotd().isEmpty()) {
            return;
        }
        Result<SettingSnapshot> current = serverConfig.read(MOTD, Optional.empty());
        if (current.isSuccess() && current.value().value().equals(now.motd())) {
            Result<SettingSnapshot> written =
                    serverConfig.write(MOTD, Optional.empty(), now.savedMotd().get());
            if (written.isFailure()) {
                log.warn("Could not put the MOTD back after maintenance: "
                        + written.error().description());
                return;
            }
        }
        MaintenanceState cleared = now.withoutSavedMotd();
        if (store.save(cleared)) {
            state = cleared;
        }
    }

    public int kept() {
        return (int)
                players.online().stream().filter(player -> mayJoin(player.id())).count();
    }

    public String status(Translator tr) {
        MaintenanceState now = state();
        if (!now.on()) {
            return tr.t("controlcore.maintenance.status_off");
        }
        String since = now.since().map(this::clock).orElse("?");
        return now.until().isPresent()
                ? tr.t(
                        "controlcore.maintenance.status_on_until",
                        "time",
                        since,
                        "until",
                        clock(now.until().get()),
                        "kept",
                        kept())
                : tr.t("controlcore.maintenance.status_on", "time", since, "kept", kept());
    }

    private String clock(Instant instant) {
        return CLOCK.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static Result<Optional<Duration>> offAfter(String text) {
        String value = clean(text).toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("never")) {
            return Result.success(Optional.empty());
        }
        Result<Optional<Duration>> parsed = BansService.duration(value);
        if (parsed.isSuccess() && parsed.value().isPresent()) {
            return parsed;
        }
        return Result.failure(Error.validation(
                "Maintenance.BadLength",
                Text.key(
                                "controlcore.errors.maintenance.length",
                                "'{length}' is not a length. Use never or a number with m, h, d or w, for example 30m or 2h.")
                        .with("length", clean(text))));
    }

    public MaintenanceView viewedBy(PlayerId viewer) {
        Optional<PlayerId> who = Optional.of(viewer);
        Translator tr = texts.forPlayer(who);
        return new MaintenanceView() {
            @Override
            public boolean on() {
                return state().on();
            }

            @Override
            public String status() {
                return MaintenanceService.this.status(tr);
            }

            @Override
            public String message() {
                return state().message();
            }

            @Override
            public String motd() {
                return state().motd();
            }

            @Override
            public List<Choice> offAfterChoices() {
                return OFF_AFTER.stream()
                        .map(id -> new Choice(id, tr.t("controlcore.maintenance.off_after." + id)))
                        .toList();
            }

            @Override
            public Result<String> turnOn(String message, String motd, String offAfter) {
                return offAfter(offAfter)
                        .flatMap(length -> MaintenanceService.this.turnOn(message, motd, length, who, tr));
            }

            @Override
            public Result<String> turnOff() {
                return MaintenanceService.this.turnOff(who, tr);
            }
        };
    }

    private static Error forbidden() {
        return Error.forbidden(
                "Maintenance.Forbidden",
                Text.key("controlcore.errors.maintenance.forbidden", "You may not change maintenance mode."));
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip();
    }
}
