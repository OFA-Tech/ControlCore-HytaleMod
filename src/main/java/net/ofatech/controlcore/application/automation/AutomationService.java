package net.ofatech.controlcore.application.automation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.api.ActionContext;
import net.ofatech.controlcore.api.AutomationAction;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.application.snapshot.ProfileService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.ActionKind;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.automation.ConditionKind;
import net.ofatech.controlcore.domain.model.automation.CronExpression;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.model.automation.Trigger;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Source;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.AutomationRuleStore;
import net.ofatech.controlcore.domain.ports.game.CommandRunner;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class AutomationService {

    static final int MAX_RULES = 100;
    static final int MAX_NAME = 40;
    static final int MAX_TEXT = 256;
    static final int MAX_COOLDOWN = 86_400;
    public static final int MAX_ACTIONS = 8;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);

    public record Fired(Optional<PlayerInfo> player, Optional<String> world, Map<String, String> variables) {

        static Fired none() {
            return new Fired(Optional.empty(), Optional.empty(), Map.of());
        }
    }

    private final AutomationRuleStore store;
    private final AutomationSettings settings;
    private final ActionRegistry actions;
    private final SessionService sessions;
    private final NotificationService notifications;
    private final CommandRunner commands;
    private final ProfileService profiles;
    private final SettingsRegistry registry;
    private final ServerConfigEditor serverConfig;
    private final SettingPermissionPolicy policy;
    private final Permissions permissions;
    private final Players players;
    private final ServerHealth health;
    private final Clock clock;
    private final Texts texts;
    private final Log log;

    private final Map<String, Instant> lastRun = new ConcurrentHashMap<>();
    private final Map<String, Instant> timerStart = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> dailyDone = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> cronDone = new ConcurrentHashMap<>();
    private final Set<String> below = ConcurrentHashMap.newKeySet();
    private int previousOnline = -1;

    public AutomationService(
            AutomationRuleStore store,
            AutomationSettings settings,
            ActionRegistry actions,
            SessionService sessions,
            NotificationService notifications,
            CommandRunner commands,
            ProfileService profiles,
            SettingsRegistry registry,
            ServerConfigEditor serverConfig,
            SettingPermissionPolicy policy,
            Permissions permissions,
            Players players,
            ServerHealth health,
            Clock clock,
            Texts texts,
            Log log) {
        this.store = store;
        this.settings = settings;
        this.actions = actions;
        this.sessions = sessions;
        this.notifications = notifications;
        this.commands = commands;
        this.profiles = profiles;
        this.registry = registry;
        this.serverConfig = serverConfig;
        this.policy = policy;
        this.permissions = permissions;
        this.players = players;
        this.health = health;
        this.clock = clock;
        this.texts = texts;
        this.log = log;
        actions.listen((id, fired) -> fire(
                Trigger.MOD_TRIGGER,
                fired.player().flatMap(players::find),
                fired.player().flatMap(sessions::world),
                fired.variables(),
                id));
    }

    public boolean mayManage(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get());
    }

    public List<Rule> rules() {
        return store.all();
    }

    public void onJoin(PlayerInfo player, boolean first) {
        Optional<String> world = sessions.world(player.id());
        fire(Trigger.JOIN, Optional.of(player), world, Map.of(), "");
        if (first) {
            fire(Trigger.FIRST_JOIN, Optional.of(player), world, Map.of(), "");
        }
    }

    public void onLeave(PlayerInfo player, Optional<String> world) {
        fire(Trigger.LEAVE, Optional.of(player), world, Map.of(), "");
    }

    public void onWorld(PlayerInfo player, String world) {
        fire(Trigger.WORLD_ENTER, Optional.of(player), Optional.of(world), Map.of(), world);
    }

    public void onSetting(SettingId id, String before, String after) {
        fire(
                Trigger.SETTING_CHANGED,
                Optional.empty(),
                Optional.empty(),
                Map.of("setting", id.toString(), "old", before, "value", after),
                id.toString());
    }

    public synchronized void tick() {
        if (!settings.on()) {
            previousOnline = -1;
            return;
        }
        Instant now = clock.now();
        LocalDateTime local = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        int online = players.online().size();
        List<ServerHealth.WorldHealth> worlds = health.worlds();
        for (Rule rule : store.all()) {
            if (!rule.enabled()) {
                continue;
            }
            switch (rule.trigger()) {
                case EVERY_MINUTES ->
                    minutes(rule.triggerValue()).ifPresent(minutes -> {
                        Instant start = timerStart.computeIfAbsent(rule.id(), id -> now);
                        if (!now.isBefore(start.plus(Duration.ofMinutes(minutes)))) {
                            timerStart.put(rule.id(), now);
                            run(rule, Fired.none(), false);
                        }
                    });
                case DAILY_AT ->
                    clockTime(rule.triggerValue()).ifPresent(time -> {
                        LocalDate today = local.toLocalDate();
                        boolean passed = !local.toLocalTime().isBefore(time);
                        LocalDate done =
                                dailyDone.computeIfAbsent(rule.id(), id -> passed ? today : today.minusDays(1));
                        if (passed && done.isBefore(today)) {
                            dailyDone.put(rule.id(), today);
                            run(rule, Fired.none(), false);
                        }
                    });
                case CRON ->
                    CronExpression.parse(rule.triggerValue()).ifPresent(cron -> {
                        LocalDateTime minute = local.truncatedTo(ChronoUnit.MINUTES);
                        if (cron.matches(minute) && !minute.equals(cronDone.get(rule.id()))) {
                            cronDone.put(rule.id(), minute);
                            run(rule, Fired.none(), false);
                        }
                    });
                case TPS_BELOW ->
                    number(rule.triggerValue(), 1, 60).ifPresent(limit -> {
                        for (ServerHealth.WorldHealth world : worlds) {
                            long tps = Math.round(effectiveTps(world));
                            String key = rule.id() + "@" + world.name();
                            if (world.ticking() && !world.paused() && tps < limit) {
                                if (below.add(key)) {
                                    run(
                                            rule,
                                            new Fired(
                                                    Optional.empty(),
                                                    Optional.of(world.name()),
                                                    Map.of("tps", Long.toString(tps))),
                                            false);
                                }
                            } else {
                                below.remove(key);
                            }
                        }
                    });
                case ONLINE_REACHES ->
                    number(rule.triggerValue(), 1, 100_000).ifPresent(count -> {
                        if (previousOnline >= 0 && previousOnline < count && online >= count) {
                            run(rule, Fired.none(), false);
                        }
                    });
                case SERVER_EMPTY -> {
                    if (previousOnline > 0 && online == 0) {
                        run(rule, Fired.none(), false);
                    }
                }
                default -> {}
            }
        }
        previousOnline = online;
    }

    void fire(
            Trigger trigger,
            Optional<PlayerInfo> player,
            Optional<String> world,
            Map<String, String> variables,
            String subject) {
        if (RUNNING.get() || !settings.on()) {
            return;
        }
        for (Rule rule : store.all()) {
            if (rule.enabled() && rule.trigger() == trigger && matches(rule, subject)) {
                run(rule, new Fired(player, world, variables), false);
            }
        }
    }

    private static boolean matches(Rule rule, String subject) {
        String wanted = rule.triggerValue();
        return switch (rule.trigger()) {
            case WORLD_ENTER, SETTING_CHANGED -> wanted.isEmpty() || wanted.equalsIgnoreCase(subject);
            case MOD_TRIGGER -> wanted.equalsIgnoreCase(subject);
            default -> true;
        };
    }

    synchronized Result<String> run(Rule rule, Fired fired, boolean test) {
        Instant now = clock.now();
        String cooldownKey =
                rule.id() + fired.player().map(player -> ":" + player.id()).orElse("");
        if (!test) {
            Instant last = lastRun.get(cooldownKey);
            if (rule.cooldownSeconds() > 0 && last != null && now.isBefore(last.plusSeconds(rule.cooldownSeconds()))) {
                return Result.success("");
            }
            for (Rule.Condition condition : rule.conditions()) {
                if (!passes(condition, fired)) {
                    return Result.success("");
                }
            }
        }
        lastRun.put(cooldownKey, now);
        lastRun.put(rule.id(), now);
        Map<String, String> values = placeholders(rule, fired);
        Translator tr = texts.forServer();
        Actor actor = Actor.console(tr.t("controlcore.automation.actor", "name", rule.name()));
        int done = 0;
        List<String> failures = new ArrayList<>();
        RUNNING.set(true);
        try {
            for (Rule.Action action : rule.actions()) {
                Result<Boolean> result = ChangeContext.as(
                        new ChangeContext.Attribution(actor, Source.AUTOMATION, 0),
                        () -> perform(rule, action, fired, values, actor, tr));
                if (result.isSuccess()) {
                    done++;
                } else {
                    failures.add(tr.error(result.error()));
                }
            }
        } finally {
            RUNNING.set(false);
        }
        if (!failures.isEmpty()) {
            log.warn("Automation rule " + rule.name() + ": " + String.join("; ", failures));
        }
        return Result.success(Integer.toString(done) + "/" + rule.actions().size()
                + (failures.isEmpty() ? "" : " " + String.join("; ", failures)));
    }

    private Result<Boolean> perform(
            Rule rule, Rule.Action action, Fired fired, Map<String, String> values, Actor actor, Translator tr) {
        String text = fill(action.value(), values);
        Optional<PlayerId> player = fired.player().map(PlayerInfo::id);
        if (action.kind().needsPlayer() && player.isEmpty()) {
            return failure(
                    "Automation.NoPlayer",
                    Text.key(
                                    "controlcore.errors.automation.no_player",
                                    "{action} needs a player, and this trigger has none.")
                            .with(
                                    "action",
                                    tr.t("controlcore.automation.action."
                                            + action.kind().id())));
        }
        switch (action.kind()) {
            case MESSAGE_PLAYER -> notifications.message(player.get(), text);
            case MESSAGE_ALL -> notifications.broadcast(text);
            case MESSAGE_ADMINS -> notifications.toAdmins(text);
            case TITLE_PLAYER, TITLE_ALL -> {
                int bar = text.indexOf('|');
                String title = (bar < 0 ? text : text.substring(0, bar)).strip();
                String subtitle = bar < 0 ? "" : text.substring(bar + 1).strip();
                if (action.kind() == ActionKind.TITLE_ALL) {
                    notifications.titleAll(title, subtitle);
                } else {
                    notifications.title(player.get(), title, subtitle);
                }
            }
            case COMMAND_SERVER -> commands.runAsServer(command(text));
            case COMMAND_PLAYER -> commands.runAs(player.get(), command(text));
            case PROFILE -> {
                Result<String> switched = profiles.switchTo(action.value(), Optional.empty(), actor, tr);
                if (switched.isFailure()) {
                    return Result.failure(switched.error());
                }
            }
            case SET_SETTING -> {
                int equals = action.value().indexOf('=');
                Result<SettingId> id = SettingId.parse(
                        action.value().substring(0, Math.max(0, equals)).strip());
                if (id.isFailure()) {
                    return Result.failure(id.error());
                }
                Result<?> written = policy.editor(
                                ServerConfigEditor.owns(id.value()) ? serverConfig : registry, Optional.empty())
                        .write(
                                id.value(),
                                Optional.empty(),
                                fill(action.value().substring(equals + 1).strip(), values));
                if (written.isFailure()) {
                    return Result.failure(written.error());
                }
            }
            case MOD_ACTION -> {
                String[] parts = text.split("\\s+", 2);
                Optional<AutomationAction> found = actions.action(parts[0]);
                if (found.isEmpty()) {
                    return failure(
                            "Automation.UnknownAction",
                            Text.key("controlcore.errors.automation.unknown_action", "No mod offers the action {id}.")
                                    .with("id", parts[0]));
                }
                found.get()
                        .work()
                        .accept(new ActionContext(rule.name(), player, parts.length > 1 ? parts[1] : "", values));
            }
        }
        return Result.success(true);
    }

    private boolean passes(Rule.Condition condition, Fired fired) {
        String value = condition.value();
        return switch (condition.kind()) {
            case HAS_PERMISSION ->
                fired.player()
                        .map(player -> permissions.has(player.id(), value))
                        .orElse(false);
            case LACKS_PERMISSION ->
                fired.player()
                        .map(player -> !permissions.has(player.id(), value))
                        .orElse(false);
            case IN_WORLD -> fired.world().map(value::equalsIgnoreCase).orElse(false);
            case ONLINE_BETWEEN ->
                range(value)
                        .map(range -> {
                            int online = players.online().size();
                            return online >= range[0] && online <= range[1];
                        })
                        .orElse(false);
            case TIME_BETWEEN ->
                timeRange(value)
                        .map(range -> {
                            LocalTime now = LocalTime.ofInstant(clock.now(), ZoneId.systemDefault());
                            return range[0].isAfter(range[1])
                                    ? !now.isBefore(range[0]) || now.isBefore(range[1])
                                    : !now.isBefore(range[0]) && now.isBefore(range[1]);
                        })
                        .orElse(false);
        };
    }

    private Map<String, String> placeholders(Rule rule, Fired fired) {
        Map<String, String> values = new HashMap<>(fired.variables());
        values.put("rule", rule.name());
        values.put("player", fired.player().map(PlayerInfo::name).orElse(""));
        values.put("world", fired.world().orElse(""));
        values.put("online", Integer.toString(players.online().size()));
        values.put("max", Integer.toString(health.maxPlayers()));
        values.putIfAbsent(
                "tps",
                health.worlds().stream()
                        .filter(world -> fired.world()
                                .map(world.name()::equalsIgnoreCase)
                                .orElse(true))
                        .findFirst()
                        .map(world -> Long.toString(Math.round(effectiveTps(world))))
                        .orElse(""));
        values.put("time", CLOCK.format(LocalTime.ofInstant(clock.now(), ZoneId.systemDefault())));
        return values;
    }

    static String fill(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    static double effectiveTps(ServerHealth.WorldHealth world) {
        return world.tickMillis() > 0 ? Math.min(world.targetTps(), 1000.0 / world.tickMillis()) : world.targetTps();
    }

    public AutomationView viewedBy(PlayerInfo viewer) {
        Translator tr = texts.forPlayer(Optional.of(viewer.id()));
        Optional<PlayerId> who = Optional.of(viewer.id());
        return new AutomationView() {
            @Override
            public List<Entry> list() {
                return AutomationService.this.entries(tr);
            }

            @Override
            public boolean globallyOn() {
                return settings.on();
            }

            @Override
            public Optional<Form> form(String id) {
                return find(id).map(AutomationService::form);
            }

            @Override
            public Result<String> save(Optional<String> editing, Form form) {
                return AutomationService.this.save(editing, form, who, tr);
            }

            @Override
            public Result<String> toggle(String id) {
                return AutomationService.this.toggle(id, who, tr);
            }

            @Override
            public Result<String> test(String id) {
                return AutomationService.this.test(id, Optional.of(viewer), who, tr);
            }

            @Override
            public Result<String> remove(String id) {
                return AutomationService.this.remove(id, who, tr);
            }

            @Override
            public String hint(String kind, String value) {
                return AutomationService.hint(kind, value, tr);
            }
        };
    }

    public List<AutomationView.Entry> entries(Translator tr) {
        List<AutomationView.Entry> entries = new ArrayList<>();
        for (Rule rule : store.all()) {
            Instant last = lastRun.get(rule.id());
            String next = nextRun(rule)
                    .map(time -> " · " + tr.t("controlcore.automation.next_run", "when", WHEN.format(time)))
                    .orElse("");
            entries.add(new AutomationView.Entry(
                    rule.id(),
                    rule.name(),
                    rule.enabled(),
                    summary(rule, tr) + next,
                    last == null
                            ? tr.t("controlcore.automation.never_ran")
                            : tr.t(
                                    "controlcore.automation.last_ran",
                                    "when",
                                    WHEN.format(LocalDateTime.ofInstant(last, ZoneId.systemDefault())))));
        }
        return entries;
    }

    Optional<LocalDateTime> nextRun(Rule rule) {
        if (!rule.enabled()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.now(), ZoneId.systemDefault());
        return switch (rule.trigger()) {
            case CRON -> CronExpression.parse(rule.triggerValue()).flatMap(cron -> cron.next(now));
            case DAILY_AT ->
                clockTime(rule.triggerValue()).map(time -> {
                    LocalDateTime today = now.toLocalDate().atTime(time);
                    return today.isAfter(now) ? today : today.plusDays(1);
                });
            default -> Optional.empty();
        };
    }

    public static String summary(Rule rule, Translator tr) {
        String trigger = tr.t("controlcore.automation.trigger." + rule.trigger().id())
                + (rule.triggerValue().isEmpty() ? "" : " " + rule.triggerValue());
        List<String> conditions = new ArrayList<>();
        for (Rule.Condition condition : rule.conditions()) {
            conditions.add(
                    tr.t("controlcore.automation.condition." + condition.kind().id()) + " " + condition.value());
        }
        List<String> actionTexts = new ArrayList<>();
        for (Rule.Action action : rule.actions()) {
            actionTexts.add(
                    tr.t("controlcore.automation.action." + action.kind().id()) + ": " + action.value());
        }
        String text = tr.t("controlcore.automation.summary.when", "trigger", trigger);
        if (!conditions.isEmpty()) {
            text += " · " + tr.t("controlcore.automation.summary.if", "conditions", String.join(", ", conditions));
        }
        text += " · " + tr.t("controlcore.automation.summary.then", "actions", String.join(" + ", actionTexts));
        if (rule.cooldownSeconds() > 0) {
            text += " · " + tr.t("controlcore.automation.summary.cooldown", "seconds", rule.cooldownSeconds());
        }
        return text;
    }

    static String hint(String kind, String value, Translator tr) {
        if (value == null || value.isBlank() || value.equals("none")) {
            return "";
        }
        if (kind.equals("trigger")) {
            Optional<Trigger> trigger = Trigger.of(value);
            if (trigger.isEmpty() || trigger.get().value() == Trigger.Value.NONE) {
                return "";
            }
        }
        return tr.t("controlcore.automation.hint." + kind + "." + value);
    }

    public Optional<Rule> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        return store.all().stream().filter(rule -> rule.id().equals(wanted)).findFirst();
    }

    static AutomationView.Form form(Rule rule) {
        Optional<Rule.Condition> condition = rule.conditions().stream().findFirst();
        List<AutomationView.Step> steps = new ArrayList<>();
        for (Rule.Action action : rule.actions()) {
            steps.add(new AutomationView.Step(action.kind().id(), action.value()));
        }
        return new AutomationView.Form(
                rule.name(),
                rule.trigger().id(),
                rule.triggerValue(),
                condition.map(c -> c.kind().id()).orElse("none"),
                condition.map(Rule.Condition::value).orElse(""),
                steps,
                Integer.toString(rule.cooldownSeconds()));
    }

    public Result<String> save(
            Optional<String> editing, AutomationView.Form form, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return forbidden();
        }
        Result<Rule> built = build(editing.flatMap(this::find), form);
        if (built.isFailure()) {
            return Result.failure(built.error());
        }
        Rule rule = built.value();
        List<Rule> rules = new ArrayList<>(store.all());
        int index = -1;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id().equals(rule.id())) {
                index = i;
            }
        }
        if (index >= 0) {
            rules.set(index, rule);
        } else if (rules.size() >= MAX_RULES) {
            return failure(
                    "Automation.TooMany",
                    Text.key("controlcore.errors.automation.too_many", "There can be at most {max} rules.")
                            .with("max", MAX_RULES));
        } else {
            rules.add(rule);
        }
        store.saveAll(rules);
        timerStart.remove(rule.id());
        dailyDone.remove(rule.id());
        cronDone.remove(rule.id());
        log.info("Automation rule saved: " + rule.name() + " (" + summary(rule, texts.forServer()) + ")");
        return Result.success(tr.t("controlcore.automation.saved", "name", rule.name()));
    }

    Result<Rule> build(Optional<Rule> existing, AutomationView.Form form) {
        String name = form.name() == null ? "" : form.name().strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            return failure(
                    "Automation.InvalidName",
                    Text.key(
                                    "controlcore.errors.automation.invalid_name",
                                    "Give the rule a name of 1 to {max} characters.")
                            .with("max", MAX_NAME));
        }
        Optional<Trigger> trigger = Trigger.of(form.trigger());
        if (trigger.isEmpty()) {
            return failure(
                    "Automation.InvalidTrigger",
                    Text.key("controlcore.errors.automation.invalid_trigger", "Pick when the rule runs."));
        }
        String triggerValue =
                form.triggerValue() == null ? "" : form.triggerValue().strip();
        Result<Boolean> triggerOk = checkTrigger(trigger.get(), triggerValue);
        if (triggerOk.isFailure()) {
            return Result.failure(triggerOk.error());
        }
        List<Rule.Condition> conditions = new ArrayList<>();
        Optional<ConditionKind> condition = ConditionKind.of(form.condition());
        if (condition.isPresent()) {
            String value =
                    form.conditionValue() == null ? "" : form.conditionValue().strip();
            Result<Boolean> ok = checkCondition(condition.get(), value);
            if (ok.isFailure()) {
                return Result.failure(ok.error());
            }
            conditions.add(new Rule.Condition(condition.get(), value));
        }
        List<Rule.Action> actionList = new ArrayList<>();
        for (AutomationView.Step step : form.actions()) {
            Optional<ActionKind> kind = ActionKind.of(step.kind());
            if (kind.isEmpty()) {
                continue;
            }
            String value = step.value() == null ? "" : step.value().strip();
            Result<Boolean> ok = checkAction(kind.get(), value);
            if (ok.isFailure()) {
                return Result.failure(ok.error());
            }
            if (kind.get().needsPlayer() && !trigger.get().playerBased() && trigger.get() != Trigger.MOD_TRIGGER) {
                Translator tr = texts.forServer();
                return failure(
                        "Automation.NeedsPlayer",
                        Text.key(
                                        "controlcore.errors.automation.needs_player",
                                        "\"{action}\" needs a player, but \"{trigger}\" has none. Use a message or title for everyone, or a player trigger.")
                                .with(
                                        "action",
                                                tr.t("controlcore.automation.action."
                                                        + kind.get().id()),
                                        "trigger",
                                                tr.t("controlcore.automation.trigger."
                                                        + trigger.get().id())));
            }
            actionList.add(new Rule.Action(kind.get(), value));
        }
        if (actionList.size() > MAX_ACTIONS) {
            return failure(
                    "Automation.TooManyActions",
                    Text.key("controlcore.errors.automation.too_many_actions", "A rule can do at most {max} things.")
                            .with("max", MAX_ACTIONS));
        }
        if (actionList.isEmpty()) {
            return failure(
                    "Automation.NoAction",
                    Text.key("controlcore.errors.automation.no_action", "Pick at least one thing the rule does."));
        }
        String cooldownText = form.cooldown() == null ? "" : form.cooldown().strip();
        Optional<Integer> cooldown = cooldownText.isEmpty()
                ? Optional.of(trigger.get().defaultCooldown())
                : number(cooldownText, 0, MAX_COOLDOWN);
        if (cooldown.isEmpty()) {
            return failure(
                    "Automation.InvalidCooldown",
                    Text.key(
                                    "controlcore.errors.automation.invalid_cooldown",
                                    "'{value}' is not a cooldown in seconds from 0 to {max}.")
                            .with("value", cooldownText, "max", MAX_COOLDOWN));
        }
        String id = existing.map(Rule::id).orElseGet(() -> freeId(name));
        return Result.success(new Rule(
                id,
                name,
                existing.map(Rule::enabled).orElse(true),
                trigger.get(),
                trigger.get().value() == Trigger.Value.NONE ? "" : triggerValue,
                conditions,
                actionList,
                cooldown.get()));
    }

    private Result<Boolean> checkTrigger(Trigger trigger, String value) {
        boolean ok = switch (trigger) {
            case EVERY_MINUTES -> minutes(value).isPresent();
            case DAILY_AT -> clockTime(value).isPresent();
            case CRON -> CronExpression.parse(value).isPresent();
            case TPS_BELOW -> number(value, 1, 60).isPresent();
            case ONLINE_REACHES -> number(value, 1, 100_000).isPresent();
            case MOD_TRIGGER -> value.matches("[a-z0-9][a-z0-9_-]*:[a-z0-9][a-z0-9_-]*");
            case SETTING_CHANGED -> value.isEmpty() || SettingId.parse(value).isSuccess();
            default -> true;
        };
        return ok
                ? Result.success(true)
                : failure(
                        "Automation.InvalidTriggerValue",
                        Text.key(
                                        "controlcore.errors.automation.invalid_trigger_value",
                                        "'{value}' does not fit: {hint}")
                                .with("value", value, "hint", hint("trigger", trigger.id(), texts.forServer())));
    }

    private Result<Boolean> checkCondition(ConditionKind kind, String value) {
        boolean ok = switch (kind) {
            case HAS_PERMISSION, LACKS_PERMISSION, IN_WORLD -> !value.isEmpty() && value.length() <= 128;
            case ONLINE_BETWEEN -> range(value).isPresent();
            case TIME_BETWEEN -> timeRange(value).isPresent();
        };
        return ok
                ? Result.success(true)
                : failure(
                        "Automation.InvalidConditionValue",
                        Text.key(
                                        "controlcore.errors.automation.invalid_condition_value",
                                        "'{value}' does not fit: {hint}")
                                .with("value", value, "hint", hint("condition", kind.id(), texts.forServer())));
    }

    private Result<Boolean> checkAction(ActionKind kind, String value) {
        boolean ok = switch (kind) {
            case PROFILE -> profiles.ids().contains(value);
            case SET_SETTING ->
                value.indexOf('=') > 0
                        && SettingId.parse(
                                        value.substring(0, value.indexOf('=')).strip())
                                .isSuccess();
            case MOD_ACTION -> value.matches("[a-z0-9][a-z0-9_-]*:[a-z0-9][a-z0-9_-]*(\\s.*)?");
            default -> !value.isEmpty() && value.length() <= MAX_TEXT;
        };
        return ok
                ? Result.success(true)
                : failure(
                        "Automation.InvalidActionValue",
                        Text.key("controlcore.errors.automation.invalid_action_value", "'{value}' does not fit: {hint}")
                                .with("value", value, "hint", hint("action", kind.id(), texts.forServer())));
    }

    private String freeId(String name) {
        String base =
                name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "rule";
        }
        String id = base;
        for (int n = 2; find(id).isPresent(); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    public Result<String> toggle(String id, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return forbidden();
        }
        Optional<Rule> found = find(id);
        if (found.isEmpty()) {
            return unknown(id);
        }
        Rule updated = found.get().withEnabled(!found.get().enabled());
        List<Rule> rules = new ArrayList<>();
        for (Rule rule : store.all()) {
            rules.add(rule.id().equals(updated.id()) ? updated : rule);
        }
        store.saveAll(rules);
        timerStart.remove(updated.id());
        return Result.success(tr.t(
                updated.enabled() ? "controlcore.automation.turned_on" : "controlcore.automation.turned_off",
                "name",
                updated.name()));
    }

    public Result<String> test(String id, Optional<PlayerInfo> tester, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return forbidden();
        }
        Optional<Rule> found = find(id);
        if (found.isEmpty()) {
            return unknown(id);
        }
        Rule rule = found.get();
        Result<String> result =
                run(rule, new Fired(tester, tester.flatMap(player -> sessions.world(player.id())), Map.of()), true);
        String[] parts = result.value().split(" ", 2);
        return Result.success(tr.t("controlcore.automation.tested", "name", rule.name(), "done", parts[0])
                + (parts.length > 1 ? " " + parts[1] : ""));
    }

    public Result<String> remove(String id, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return forbidden();
        }
        Optional<Rule> found = find(id);
        if (found.isEmpty()) {
            return unknown(id);
        }
        List<Rule> rules = new ArrayList<>(store.all());
        rules.removeIf(rule -> rule.id().equals(found.get().id()));
        store.saveAll(rules);
        log.info("Automation rule removed: " + found.get().name());
        return Result.success(
                tr.t("controlcore.automation.removed", "name", found.get().name()));
    }

    public Map<String, String> modActions() {
        Map<String, String> found = new LinkedHashMap<>();
        actions.actions().forEach(action -> found.put(action.id(), action.displayName()));
        return found;
    }

    public Map<String, String> modTriggers() {
        return actions.triggers();
    }

    private static String command(String text) {
        String stripped = text.strip();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    static Optional<Integer> minutes(String text) {
        return number(text, 1, 1440);
    }

    static Optional<Integer> number(String text, int min, int max) {
        String value = text == null ? "" : text.strip();
        if (!value.matches("\\d{1,7}")) {
            return Optional.empty();
        }
        int number = Integer.parseInt(value);
        return number >= min && number <= max ? Optional.of(number) : Optional.empty();
    }

    static Optional<LocalTime> clockTime(String text) {
        try {
            return Optional.of(LocalTime.parse(text == null ? "" : text.strip(), CLOCK));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    static Optional<int[]> range(String text) {
        String[] parts = (text == null ? "" : text).split("-");
        if (parts.length != 2) {
            return Optional.empty();
        }
        Optional<Integer> low = number(parts[0], 0, 100_000);
        Optional<Integer> high = number(parts[1], 0, 100_000);
        return low.isPresent() && high.isPresent() && low.get() <= high.get()
                ? Optional.of(new int[] {low.get(), high.get()})
                : Optional.empty();
    }

    static Optional<LocalTime[]> timeRange(String text) {
        String[] parts = (text == null ? "" : text).split("-");
        if (parts.length != 2) {
            return Optional.empty();
        }
        Optional<LocalTime> from = clockTime(parts[0]);
        Optional<LocalTime> to = clockTime(parts[1]);
        return from.isPresent() && to.isPresent() && !from.get().equals(to.get())
                ? Optional.of(new LocalTime[] {from.get(), to.get()})
                : Optional.empty();
    }

    private static <T> Result<T> failure(String code, Text message) {
        return Result.failure(Error.validation(code, message));
    }

    private <T> Result<T> unknown(String id) {
        return Result.failure(Error.notFound(
                "Automation.Unknown",
                Text.key("controlcore.errors.automation.unknown", "There is no rule {id}.")
                        .with("id", id == null ? "" : id)));
    }

    private static <T> Result<T> forbidden() {
        return Result.failure(Error.forbidden(
                "Automation.Forbidden",
                Text.key("controlcore.errors.automation.forbidden", "Only admins can manage automation rules.")));
    }
}
