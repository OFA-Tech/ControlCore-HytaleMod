package net.ofatech.controlcore.application.bans;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.history.ChangeHistory;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.bans.BansView;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.Bans;
import net.ofatech.controlcore.domain.ports.game.Bans.Ban;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.system.Clock;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class BansService {

    public static final String NODE = "bans";
    public static final String TARGET = "ban:";
    static final List<String> LENGTHS = List.of("permanent", "1h", "1d", "7d", "30d");

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern LENGTH = Pattern.compile("(\\d{1,4})\\s*([mhdw])");
    private static final String SEPARATOR = "\u001f";

    private final Bans bans;
    private final ChangeHistory history;
    private final SettingPermissionPolicy policy;
    private final Permissions permissions;
    private final Texts texts;
    private final Clock clock;
    private final Map<PlayerId, String> names = new ConcurrentHashMap<>();
    private final Set<PlayerId> asked = ConcurrentHashMap.newKeySet();
    private final Map<PlayerId, String> lastSeen = new ConcurrentHashMap<>();
    private volatile boolean seen;

    public BansService(
            Bans bans,
            ChangeHistory history,
            SettingPermissionPolicy policy,
            Permissions permissions,
            Texts texts,
            Clock clock) {
        this.bans = bans;
        this.history = history;
        this.policy = policy;
        this.permissions = permissions;
        this.texts = texts;
        this.clock = clock;
    }

    public boolean mayManage(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get()) || permissions.hasOwn(who.get(), NODE);
    }

    public void remember(PlayerId id, String name) {
        if (name != null && !name.isBlank()) {
            names.put(id, name);
        }
    }

    public String name(PlayerId id) {
        String known = names.get(id);
        if (known != null) {
            return known;
        }
        if (asked.add(id)) {
            bans.nameOf(id).thenAccept(found -> found.ifPresent(name -> remember(id, name)));
        }
        return id.value().toString();
    }

    public List<Ban> all() {
        return bans.all().stream()
                .sorted(Comparator.comparing(Ban::at).reversed())
                .toList();
    }

    public Optional<Ban> find(PlayerId id) {
        return bans.all().stream().filter(ban -> ban.target().equals(id)).findFirst();
    }

    public String details(Ban ban, Translator tr) {
        Instant now = clock.now();
        String length = ban.expires().isEmpty()
                ? tr.t("controlcore.bans.permanent")
                : ban.inEffect(now)
                        ? tr.t(
                                "controlcore.bans.until",
                                "when",
                                when(ban.expires().get()))
                        : tr.t(
                                "controlcore.bans.expired",
                                "when",
                                when(ban.expires().get()));
        return tr.t(
                "controlcore.bans.details",
                "when",
                when(ban.at()),
                "who",
                ban.by().map(this::name).orElse(tr.t("controlcore.bans.console")),
                "length",
                length,
                "reason",
                ban.reason().isBlank() ? tr.t("controlcore.bans.no_reason") : ban.reason());
    }

    public String when(Instant instant) {
        return WHEN.format(instant.atZone(ZoneId.systemDefault()));
    }

    public CompletableFuture<Result<String>> ban(
            Optional<PlayerId> who, String player, String length, String reason, Translator tr) {
        if (!mayManage(who)) {
            return CompletableFuture.completedFuture(Result.failure(forbidden()));
        }
        Result<Optional<Duration>> duration = duration(length);
        if (duration.isFailure()) {
            return CompletableFuture.completedFuture(Result.failure(duration.error()));
        }
        String input = player == null ? "" : player.strip();
        if (input.isEmpty()) {
            return CompletableFuture.completedFuture(Result.failure(Error.validation(
                    "Bans.NoPlayer", Text.key("controlcore.errors.bans.no_player", "Type a player name first."))));
        }
        ChangeContext.Attribution attribution = ChangeContext.current();
        return bans.lookup(input)
                .thenApply(profile -> ChangeContext.as(attribution, () -> {
                    if (profile.isEmpty()) {
                        return Result.<String>failure(Error.notFound(
                                "Bans.UnknownPlayer",
                                Text.key(
                                                "controlcore.errors.bans.unknown_player",
                                                "No Hytale player is called {player}.")
                                        .with("player", input)));
                    }
                    PlayerId target = profile.get().id();
                    remember(target, profile.get().name());
                    if (find(target).filter(ban -> ban.inEffect(clock.now())).isPresent()) {
                        return Result.<String>failure(Error.validation(
                                "Bans.AlreadyBanned",
                                Text.key("controlcore.errors.bans.already", "{player} is already banned.")
                                        .with("player", name(target))));
                    }
                    if (who.isPresent() && who.get().equals(target)) {
                        return Result.<String>failure(Error.validation(
                                "Bans.Self", Text.key("controlcore.errors.bans.self", "You can't ban yourself.")));
                    }
                    Instant now = clock.now();
                    Ban ban = new Ban(
                            target, who, now, duration.value().map(now::plus), reason == null ? "" : reason.strip());
                    String before = find(target).map(BansService::encode).orElse("");
                    if (!bans.ban(ban)) {
                        return Result.<String>failure(Error.failure(
                                "Bans.Failed",
                                Text.key("controlcore.errors.bans.failed", "The ban list could not be changed.")));
                    }
                    String after = encode(ban);
                    lastSeen.put(target, after);
                    history.record(Kind.BAN, TARGET + target.value(), before, after, false);
                    return Result.success(tr.t(
                            "controlcore.bans.banned",
                            "player",
                            name(target),
                            "length",
                            ban.expires()
                                    .map(end -> tr.t("controlcore.bans.until", "when", when(end)))
                                    .orElse(tr.t("controlcore.bans.permanent"))));
                }));
    }

    public Result<String> unban(Optional<PlayerId> who, String player, Translator tr) {
        if (!mayManage(who)) {
            return Result.failure(forbidden());
        }
        Optional<Ban> found = matching(player);
        if (found.isEmpty()) {
            return Result.failure(Error.notFound(
                    "Bans.NotBanned",
                    Text.key("controlcore.errors.bans.not_banned", "{player} is not banned.")
                            .with("player", player == null ? "" : player.strip())));
        }
        Ban ban = found.get();
        if (!bans.unban(ban.target())) {
            return Result.failure(Error.failure(
                    "Bans.Failed", Text.key("controlcore.errors.bans.failed", "The ban list could not be changed.")));
        }
        lastSeen.remove(ban.target());
        history.record(Kind.BAN, TARGET + ban.target().value(), encode(ban), "", false);
        return Result.success(tr.t("controlcore.bans.unbanned", "player", name(ban.target())));
    }

    public Result<?> restore(String target, String encoded, Optional<PlayerId> who) {
        Optional<PlayerId> id = idOf(target);
        if (id.isEmpty()) {
            return Result.failure(Error.notFound("Bans.Unknown", target));
        }
        if (encoded.isEmpty()) {
            if (find(id.get()).isEmpty()) {
                return Result.success(true);
            }
            bans.unban(id.get());
            lastSeen.remove(id.get());
            history.record(
                    Kind.BAN, target, find(id.get()).map(BansService::encode).orElse(""), "", false);
            return Result.success(true);
        }
        Ban ban = decode(id.get(), encoded, who, clock.now());
        String before = find(id.get()).map(BansService::encode).orElse("");
        if (find(id.get()).isPresent()) {
            bans.unban(id.get());
        }
        bans.ban(ban);
        String after = encode(ban);
        lastSeen.put(id.get(), after);
        history.record(Kind.BAN, target, before, after, false);
        return Result.success(true);
    }

    public synchronized void checkOutsideChanges() {
        Map<PlayerId, String> now = new ConcurrentHashMap<>();
        for (Ban ban : bans.all()) {
            now.put(ban.target(), encode(ban));
        }
        if (!seen) {
            seen = true;
            lastSeen.putAll(now);
            return;
        }
        List<PlayerId> changed = new ArrayList<>();
        now.forEach((id, value) -> {
            if (!value.equals(lastSeen.get(id))) {
                changed.add(id);
            }
        });
        lastSeen.keySet().forEach(id -> {
            if (!now.containsKey(id)) {
                changed.add(id);
            }
        });
        for (PlayerId id : changed) {
            String before = lastSeen.getOrDefault(id, "");
            String after = now.getOrDefault(id, "");
            ChangeContext.fromOutside(() -> history.record(Kind.BAN, TARGET + id.value(), before, after, false));
        }
        lastSeen.clear();
        lastSeen.putAll(now);
    }

    public String what(String target, Translator tr) {
        return tr.t(
                "controlcore.history.what.ban",
                "player",
                idOf(target).map(this::name).orElse(target));
    }

    public String shown(String value, Translator tr) {
        if (value.isEmpty()) {
            return tr.t("controlcore.history.value.not_banned");
        }
        Ban ban = decode(PlayerId.of(new UUID(0, 0)), value, Optional.empty(), Instant.EPOCH);
        String length = ban.expires()
                .map(end -> tr.t("controlcore.bans.until", "when", when(end)))
                .orElse(tr.t("controlcore.bans.permanent"));
        return ban.reason().isBlank()
                ? tr.t("controlcore.history.value.banned", "length", length)
                : tr.t("controlcore.history.value.banned_reason", "length", length, "reason", ban.reason());
    }

    public BansView viewedBy(PlayerId viewer) {
        Translator tr = texts.forPlayer(Optional.of(viewer));
        Optional<PlayerId> who = Optional.of(viewer);
        return new BansView() {
            @Override
            public List<Entry> list() {
                Instant now = clock.now();
                return all().stream()
                        .map(ban -> new Entry(
                                ban.target().value().toString(),
                                name(ban.target()),
                                details(ban, tr),
                                ban.inEffect(now)))
                        .toList();
            }

            @Override
            public List<Length> lengths() {
                return LENGTHS.stream()
                        .map(id -> new Length(id, tr.t("controlcore.bans.length." + id)))
                        .toList();
            }

            @Override
            public CompletableFuture<Result<String>> ban(String player, String length, String reason) {
                return BansService.this.ban(who, player, length, reason, tr);
            }

            @Override
            public Result<String> unban(String id) {
                return BansService.this.unban(who, id, tr);
            }
        };
    }

    static Result<Optional<Duration>> duration(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("permanent") || value.equals("perm") || value.equals("forever")) {
            return Result.success(Optional.empty());
        }
        Matcher matcher = LENGTH.matcher(value);
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            if (amount > 0) {
                Duration duration = switch (matcher.group(2)) {
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    case "d" -> Duration.ofDays(amount);
                    default -> Duration.ofDays(amount * 7);
                };
                return Result.success(Optional.of(duration));
            }
        }
        return Result.failure(Error.validation(
                "Bans.BadLength",
                Text.key(
                                "controlcore.errors.bans.length",
                                "'{length}' is not a ban length. Use permanent or a number with m, h, d or w,"
                                        + " for example 30m, 12h, 7d.")
                        .with("length", text == null ? "" : text)));
    }

    private Optional<Ban> matching(String player) {
        String text = player == null ? "" : player.strip();
        return bans.all().stream()
                .filter(ban -> ban.target().value().toString().equalsIgnoreCase(text)
                        || name(ban.target()).equalsIgnoreCase(text))
                .findFirst();
    }

    private static Optional<PlayerId> idOf(String target) {
        String text = target.startsWith(TARGET) ? target.substring(TARGET.length()) : target;
        try {
            return Optional.of(PlayerId.of(UUID.fromString(text)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static String encode(Ban ban) {
        return ban.expires().map(Instant::toString).orElse("") + SEPARATOR + ban.reason();
    }

    static Ban decode(PlayerId target, String value, Optional<PlayerId> by, Instant at) {
        int split = value.indexOf(SEPARATOR);
        String expires = split < 0 ? "" : value.substring(0, split);
        String reason = split < 0 ? value : value.substring(split + 1);
        Optional<Instant> end;
        try {
            end = expires.isEmpty() ? Optional.empty() : Optional.of(Instant.parse(expires));
        } catch (RuntimeException e) {
            end = Optional.empty();
        }
        return new Ban(target, by, at, end, reason);
    }

    private static Error forbidden() {
        return Error.forbidden(
                "Bans.Forbidden", Text.key("controlcore.errors.bans.forbidden", "Only admins can manage bans."));
    }
}
