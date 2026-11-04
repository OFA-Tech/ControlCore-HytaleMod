package net.ofatech.controlcore.application.automation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.CommonErrors;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.automation.Announcements;
import net.ofatech.controlcore.domain.model.automation.Announcements.Channel;
import net.ofatech.controlcore.domain.model.automation.Announcements.Item;
import net.ofatech.controlcore.domain.model.automation.Announcements.Order;
import net.ofatech.controlcore.domain.model.automation.AnnouncementsView;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.config.AnnouncementStore;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class AnnouncementService {

    public static final String NODE = "announcements";
    public static final String SETTINGS = "settings";
    public static final String ORDER = "order";
    public static final String ITEM = "item:";
    public static final String WARNING_ID = "announcement";
    static final int MAX_ITEMS = 50;
    static final int MAX_TEXT = 256;
    static final int MAX_INTERVAL = 1440;

    private final AnnouncementStore store;
    private final NotificationService notifications;
    private final ServerPlaceholders placeholders;
    private final Players players;
    private final SettingPermissionPolicy policy;
    private final Permissions permissions;
    private final ChangeHistory history;
    private final Clock clock;
    private final Texts texts;
    private Announcements state;
    private Optional<String> lastSent = Optional.empty();
    private Optional<Instant> timerStart = Optional.empty();

    public AnnouncementService(
            AnnouncementStore store,
            NotificationService notifications,
            ServerPlaceholders placeholders,
            Players players,
            SettingPermissionPolicy policy,
            Permissions permissions,
            ChangeHistory history,
            Clock clock,
            Texts texts) {
        this.store = store;
        this.notifications = notifications;
        this.placeholders = placeholders;
        this.players = players;
        this.policy = policy;
        this.permissions = permissions;
        this.history = history;
        this.clock = clock;
        this.texts = texts;
    }

    public boolean mayManage(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), NODE);
    }

    public synchronized Announcements state() {
        if (state == null) {
            state = store.load();
        }
        return state;
    }

    public synchronized void tick() {
        Announcements now = state();
        Instant at = clock.now();
        if (!now.enabled()) {
            timerStart = Optional.empty();
            return;
        }
        Instant start = timerStart.orElse(at);
        timerStart = Optional.of(start);
        if (at.isBefore(start.plus(Duration.ofMinutes(now.intervalMinutes())))) {
            return;
        }
        timerStart = Optional.of(at);
        if (players.online().size() >= now.minPlayers()) {
            next(now).ifPresent(item -> deliver(now, item));
        }
    }

    Optional<Item> next(Announcements now) {
        List<Item> enabled = now.items().stream().filter(Item::enabled).toList();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        if (now.order() == Order.RANDOM) {
            List<Item> choices = enabled.size() > 1
                    ? enabled.stream()
                            .filter(item -> lastSent.filter(item.id()::equals).isEmpty())
                            .toList()
                    : enabled;
            return Optional.of(choices.get(ThreadLocalRandom.current().nextInt(choices.size())));
        }
        int last = lastSent.map(id -> indexOf(now.items(), id)).orElse(-1);
        for (int step = 1; step <= now.items().size(); step++) {
            Item candidate =
                    now.items().get(Math.floorMod(last + step, now.items().size()));
            if (candidate.enabled()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String deliver(Announcements now, Item item) {
        String text = placeholders.fill(item.text());
        lastSent = Optional.of(item.id());
        AutomationService.Leveled leveled = AutomationService.Leveled.of(text);
        switch (now.channel()) {
            case CHAT -> notifications.broadcast(text);
            case TOAST -> {
                AutomationService.HudText shown = AutomationService.HudText.toast(leveled.text());
                notifications.toastAll(shown.title(), shown.body(), leveled.level(), AutomationService.TOAST_SECONDS);
            }
            case WARNING ->
                notifications.warningAll(WARNING_ID, leveled.text(), leveled.level(), now.intervalMinutes() * 60);
        }
        return text;
    }

    public synchronized Result<String> sendNow(Optional<String> id, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Announcements now = state();
        Optional<Item> item = id.isPresent() ? now.find(id.get()) : next(now);
        if (item.isEmpty()) {
            return Result.failure(id.isPresent() ? unknown(id.get()) : nothingToSend());
        }
        return Result.success(tr.t("controlcore.announcements.sent", "text", deliver(now, item.get())));
    }

    public synchronized Result<String> add(String text, Optional<PlayerId> who, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        String value = clean(text);
        if (value.isEmpty() || value.length() > MAX_TEXT) {
            return Result.failure(Error.validation(
                    "Announcements.InvalidText",
                    Text.key(
                            "controlcore.errors.announcements.invalid_text",
                            "Write the announcement, up to 256 characters.")));
        }
        Announcements now = state();
        if (now.items().size() >= MAX_ITEMS) {
            return Result.failure(Error.validation(
                    "Announcements.TooMany",
                    Text.key("controlcore.errors.announcements.too_many", "There can be at most {max} announcements.")
                            .with("max", MAX_ITEMS)));
        }
        Item item = new Item(freeId(now), value, true);
        List<Item> items = new ArrayList<>(now.items());
        items.add(item);
        return save(now.withItems(items), ITEM + item.id(), "", encode(item))
                .map(ok -> tr.t("controlcore.announcements.added", "id", item.id()));
    }

    public synchronized Result<String> remove(String id, Optional<PlayerId> who, Translator tr) {
        Result<Item> found = find(id, who);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        Announcements now = state();
        List<Item> items = new ArrayList<>(now.items());
        items.removeIf(item -> item.id().equals(found.value().id()));
        return save(now.withItems(items), ITEM + found.value().id(), encode(found.value()), "")
                .map(ok -> tr.t(
                        "controlcore.announcements.removed", "id", found.value().id()));
    }

    public synchronized Result<String> setItemEnabled(String id, boolean on, Optional<PlayerId> who, Translator tr) {
        Result<Item> found = find(id, who);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        Item before = found.value();
        Item after = new Item(before.id(), before.text(), on);
        if (before.enabled() != on) {
            Announcements now = state();
            Result<Boolean> saved = save(
                    now.withItems(now.items().stream()
                            .map(item -> item.id().equals(before.id()) ? after : item)
                            .toList()),
                    ITEM + before.id(),
                    encode(before),
                    encode(after));
            if (saved.isFailure()) {
                return Result.failure(saved.error());
            }
        }
        return Result.success(tr.t(
                on ? "controlcore.announcements.item_on" : "controlcore.announcements.item_off", "id", before.id()));
    }

    public synchronized Result<String> move(String id, int by, Optional<PlayerId> who, Translator tr) {
        Result<Item> found = find(id, who);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        Announcements now = state();
        List<Item> items = new ArrayList<>(now.items());
        int from = indexOf(items, found.value().id());
        int to = from + by;
        if (by == 0 || to < 0 || to >= items.size()) {
            return Result.failure(Error.validation(
                    "Announcements.CannotMove",
                    Text.key("controlcore.errors.announcements.cannot_move", "Announcement {id} can't move further.")
                            .with("id", found.value().id())));
        }
        items.add(to, items.remove(from));
        return save(now.withItems(items), ORDER, order(now.items()), order(items))
                .map(ok -> tr.t(
                        "controlcore.announcements.moved", "id", found.value().id(), "place", to + 1));
    }

    public synchronized Result<String> setSettings(
            Optional<Boolean> enabled,
            Optional<String> interval,
            Optional<String> order,
            Optional<String> channel,
            Optional<String> minPlayers,
            Optional<PlayerId> who,
            Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Announcements now = state();
        Optional<Integer> minutes =
                interval.map(text -> number(text, 1, MAX_INTERVAL)).orElse(Optional.of(now.intervalMinutes()));
        if (minutes.isEmpty()) {
            return Result.failure(invalid(
                    "Announcements.InvalidInterval",
                    "controlcore.errors.announcements.interval",
                    "The interval is a number of minutes from 1 to 1440."));
        }
        Optional<Order> newOrder = order.map(Order::of).orElse(Optional.of(now.order()));
        if (newOrder.isEmpty()) {
            return Result.failure(CommonErrors.badChoice("Announcements.InvalidOrder", "in-turn, random"));
        }
        Optional<Channel> newChannel = channel.map(Channel::of).orElse(Optional.of(now.channel()));
        if (newChannel.isEmpty()) {
            return Result.failure(CommonErrors.badChoice("Announcements.InvalidChannel", "chat, toast, warning"));
        }
        Optional<Integer> min = minPlayers.map(text -> number(text, 0, 100_000)).orElse(Optional.of(now.minPlayers()));
        if (min.isEmpty()) {
            return Result.failure(invalid(
                    "Announcements.InvalidMinPlayers",
                    "controlcore.errors.announcements.min_players",
                    "The minimum players online is a number from 0 up."));
        }
        Announcements next = now.withSettings(
                enabled.orElse(now.enabled()), minutes.get(), newOrder.get(), newChannel.get(), min.get());
        String before = settings(now);
        String after = settings(next);
        if (!before.equals(after)) {
            Result<Boolean> saved = save(next, SETTINGS, before, after);
            if (saved.isFailure()) {
                return Result.failure(saved.error());
            }
            if (!next.enabled()) {
                notifications.clearWarningAll(WARNING_ID);
            }
        }
        return Result.success(describe(next, tr));
    }

    public synchronized Result<?> restore(Announcements wanted, Optional<PlayerId> who) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Announcements now = state();
        if (now.equals(wanted)) {
            return Result.success(true);
        }
        if (!store.save(wanted)) {
            return Result.failure(CommonErrors.notSaved());
        }
        state = wanted;
        history.record(Kind.ANNOUNCEMENT, SETTINGS, settings(now), settings(wanted), false);
        for (Item item : now.items()) {
            history.record(
                    Kind.ANNOUNCEMENT,
                    ITEM + item.id(),
                    encode(item),
                    wanted.find(item.id()).map(AnnouncementService::encode).orElse(""),
                    false);
        }
        for (Item item : wanted.items()) {
            if (now.find(item.id()).isEmpty()) {
                history.record(Kind.ANNOUNCEMENT, ITEM + item.id(), "", encode(item), false);
            }
        }
        history.record(Kind.ANNOUNCEMENT, ORDER, order(now.items()), order(wanted.items()), false);
        return Result.success(true);
    }

    public synchronized Result<?> rollback(String target, String old, Optional<PlayerId> who) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Announcements now = state();
        if (target.equals(SETTINGS)) {
            Announcements parsed = parseSettings(old, now);
            return save(parsed, SETTINGS, settings(now), settings(parsed));
        }
        if (target.equals(ORDER)) {
            List<Item> items = new ArrayList<>();
            for (String id : old.split(",")) {
                now.find(id).ifPresent(items::add);
            }
            now.items().stream().filter(item -> !items.contains(item)).forEach(items::add);
            return save(now.withItems(items), ORDER, order(now.items()), order(items));
        }
        String id = target.substring(ITEM.length());
        Optional<Item> current = now.find(id);
        Optional<Item> wanted = decode(id, old);
        List<Item> items = new ArrayList<>(now.items());
        if (wanted.isEmpty()) {
            items.removeIf(item -> item.id().equals(id));
        } else if (current.isPresent()) {
            items.replaceAll(item -> item.id().equals(id) ? wanted.get() : item);
        } else {
            items.add(wanted.get());
        }
        return save(
                now.withItems(items),
                target,
                current.map(AnnouncementService::encode).orElse(""),
                wanted.map(AnnouncementService::encode).orElse(""));
    }

    private Result<Boolean> save(Announcements next, String target, String before, String after) {
        if (next.items().size() > MAX_ITEMS) {
            return Result.failure(Error.validation(
                    "Announcements.TooMany",
                    Text.key("controlcore.errors.announcements.too_many", "There can be at most {max} announcements.")
                            .with("max", MAX_ITEMS)));
        }
        if (!store.save(next)) {
            return Result.failure(CommonErrors.notSaved());
        }
        state = next;
        history.record(Kind.ANNOUNCEMENT, target, before, after, false);
        return Result.success(true);
    }

    public String what(String target, Translator tr) {
        if (target.startsWith(ITEM)) {
            return tr.t("controlcore.history.what.announcement", "id", target.substring(ITEM.length()));
        }
        return tr.t(
                target.equals(ORDER)
                        ? "controlcore.history.what.announcement_order"
                        : "controlcore.history.what.announcement_settings");
    }

    public String shown(String target, String value, Translator tr) {
        if (value.isEmpty()) {
            return tr.t("controlcore.history.value.none");
        }
        if (target.startsWith(ITEM)) {
            return decode("x", value)
                    .map(item -> tr.t(
                            item.enabled()
                                    ? "controlcore.history.value.announcement_on"
                                    : "controlcore.history.value.announcement_off",
                            "text",
                            item.text()))
                    .orElse(value);
        }
        if (target.equals(SETTINGS)) {
            return describe(parseSettings(value, Announcements.DEFAULT), tr);
        }
        return value;
    }

    public String describe(Announcements now, Translator tr) {
        return tr.t(
                now.enabled() ? "controlcore.announcements.settings_on" : "controlcore.announcements.settings_off",
                "minutes",
                now.intervalMinutes(),
                "order",
                tr.t("controlcore.announcements.order." + now.order().id()),
                "channel",
                tr.t("controlcore.announcements.channel." + now.channel().id()),
                "min",
                now.minPlayers());
    }

    public String summary(Announcements now, Translator tr) {
        long on = now.items().stream().filter(Item::enabled).count();
        return tr.t(
                        "controlcore.announcements.summary",
                        "on",
                        on,
                        "total",
                        now.items().size()) + " · " + describe(now, tr);
    }

    static String encode(Item item) {
        return (item.enabled() ? "on|" : "off|") + item.text();
    }

    static Optional<Item> decode(String id, String value) {
        int bar = value.indexOf('|');
        if (value.isEmpty() || bar < 0) {
            return Optional.empty();
        }
        return Optional.of(
                new Item(id, value.substring(bar + 1), value.substring(0, bar).equals("on")));
    }

    static String settings(Announcements now) {
        return String.join(
                ";",
                now.enabled() ? "on" : "off",
                Integer.toString(now.intervalMinutes()),
                now.order().id(),
                now.channel().id(),
                Integer.toString(now.minPlayers()));
    }

    static Announcements parseSettings(String value, Announcements base) {
        String[] parts = value.split(";");
        if (parts.length != 5) {
            return base;
        }
        return base.withSettings(
                parts[0].equals("on"),
                number(parts[1], 1, MAX_INTERVAL).orElse(base.intervalMinutes()),
                Order.of(parts[2]).orElse(base.order()),
                Channel.of(parts[3]).orElse(base.channel()),
                number(parts[4], 0, 100_000).orElse(base.minPlayers()));
    }

    private static String order(List<Item> items) {
        return items.stream().map(Item::id).collect(Collectors.joining(","));
    }

    private Result<Item> find(String id, Optional<PlayerId> who) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        return state().find(id).map(Result::success).orElseGet(() -> Result.failure(unknown(clean(id))));
    }

    private static int indexOf(List<Item> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String freeId(Announcements now) {
        int max = now.items().stream()
                .map(Item::id)
                .filter(id -> id.matches("\\d{1,6}"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return Integer.toString(max + 1);
    }

    static Optional<Integer> number(String text, int min, int max) {
        String value = clean(text);
        if (!value.matches("\\d{1,7}")) {
            return Optional.empty();
        }
        int number = Integer.parseInt(value);
        return number >= min && number <= max ? Optional.of(number) : Optional.empty();
    }

    public AnnouncementsView viewedBy(PlayerId viewer) {
        Optional<PlayerId> who = Optional.of(viewer);
        Translator tr = texts.forPlayer(who);
        return new AnnouncementsView() {
            @Override
            public Announcements state() {
                return AnnouncementService.this.state();
            }

            @Override
            public List<Choice> orders() {
                return Arrays.stream(Order.values())
                        .map(order -> new Choice(order.id(), tr.t("controlcore.announcements.order." + order.id())))
                        .toList();
            }

            @Override
            public List<Choice> channels() {
                return Arrays.stream(Channel.values())
                        .map(channel ->
                                new Choice(channel.id(), tr.t("controlcore.announcements.channel." + channel.id())))
                        .toList();
            }

            @Override
            public Result<String> setEnabled(boolean on) {
                return setSettings(
                        Optional.of(on),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        who,
                        tr);
            }

            @Override
            public Result<String> saveSettings(String interval, String order, String channel, String minPlayers) {
                return setSettings(
                        Optional.empty(),
                        Optional.of(interval),
                        Optional.of(order),
                        Optional.of(channel),
                        Optional.of(minPlayers),
                        who,
                        tr);
            }

            @Override
            public Result<String> add(String text) {
                return AnnouncementService.this.add(text, who, tr);
            }

            @Override
            public Result<String> remove(String id) {
                return AnnouncementService.this.remove(id, who, tr);
            }

            @Override
            public Result<String> toggle(String id) {
                return AnnouncementService.this
                        .state()
                        .find(id)
                        .map(item -> setItemEnabled(id, !item.enabled(), who, tr))
                        .orElseGet(() -> Result.failure(unknown(clean(id))));
            }

            @Override
            public Result<String> move(String id, int by) {
                return AnnouncementService.this.move(id, by, who, tr);
            }

            @Override
            public Result<String> send(String id) {
                return sendNow(Optional.of(id), who, tr);
            }
        };
    }

    private static Error invalid(String code, String key, String fallback) {
        return Error.validation(code, Text.key(key, fallback));
    }

    private static Error unknown(String id) {
        return Error.notFound(
                "Announcements.Unknown",
                Text.key("controlcore.errors.announcements.unknown", "There is no announcement {id}.")
                        .with("id", id));
    }

    private static Error nothingToSend() {
        return Error.validation(
                "Announcements.NothingToSend",
                Text.key("controlcore.errors.announcements.nothing", "There is no announcement turned on to send."));
    }

    static Error forbidden() {
        return Error.forbidden(
                "Announcements.Forbidden",
                Text.key("controlcore.errors.announcements.forbidden", "You may not manage announcements."));
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip();
    }
}
