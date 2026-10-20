package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.simple.BooleanCodec;
import com.hypixel.hytale.codec.codecs.simple.ByteCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.LongCodec;
import com.hypixel.hytale.codec.codecs.simple.NullableBooleanCodec;
import com.hypixel.hytale.codec.codecs.simple.ShortCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.codec.validation.Validator;
import com.hypixel.hytale.codec.validation.validator.RangeValidator;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.setup.ViewRadius;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

/**
 * Reads and edits {@link HytaleServerConfig}, Hytale's {@code config.json}. The keys are found by walking the
 * config's codec, so new Hytale keys show up without code changes (read-only until {@link HytaleConfigAccess}
 * lists them). Effective values come from the config's getters where they exist, because keys missing from the
 * file fall back to built-in defaults inside the getters; defaults are read the same way from a fresh config.
 *
 * <p>A change is decoded into the live config object by the key's own codec field, so Hytale's validators run,
 * then the config is saved to {@code config.json} at once. A new max view radius is also pushed to online
 * players, like Hytale's {@code /maxviewradius}.
 */
public final class HytaleServerConfiguration implements ServerConfiguration {

    private static final int CHUNK_SIZE = 32;
    private static final int MAX_SECONDS = 86_400;
    private static final int MAX_DEPTH = 3;
    private static final String GENERAL = "General";
    private static final String ADVANCED = "Advanced (read-only)";
    private static final String PASSWORD = "Password";
    private static final String MAX_VIEW_RADIUS = "MaxViewRadius";

    /** Descriptions for keys Hytale does not document in its codec. */
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "ServerName",
            "Shown to players when they connect and in LAN discovery.",
            "MOTD",
            "Message of the day, shown to players when they connect.",
            PASSWORD,
            "Players need it to join. Change it in config.json.",
            "MaxPlayers",
            "Players who can be online at once; 0 or less means unlimited.",
            MAX_VIEW_RADIUS,
            "View distance limit in chunks; online players are updated at once.",
            "RequireJoinPermission",
            "Players need the join permission to connect.",
            "Version",
            "Format version of config.json.");

    /** One key found in the config codec. */
    private record Key(List<String> segments, BuilderField<?, ?> field, Codec<?> codec, String section, int group) {

        String path() {
            return String.join(".", segments);
        }

        String last() {
            return segments.getLast();
        }
    }

    private final Log log;
    private final Supplier<HytaleServerConfig> current;
    private CompletableFuture<Void> saving = CompletableFuture.completedFuture(null);

    public HytaleServerConfiguration(Log log) {
        this(log, () -> HytaleServer.get().getConfig());
    }

    HytaleServerConfiguration(Log log, Supplier<HytaleServerConfig> current) {
        this.log = log;
        this.current = current;
    }

    @Override
    public ServerSettings read() {
        HytaleServerConfig config = current.get();
        HytaleServerConfig.TimeoutProfile timeouts = config.getConnectionTimeouts();
        Duration play = timeouts != null ? timeouts.getPlay() : null;
        String password = config.getPassword();
        return new ServerSettings(
                Objects.requireNonNullElse(config.getServerName(), ""),
                Objects.requireNonNullElse(config.getMotd(), ""),
                password != null && !password.isEmpty(),
                config.getMaxPlayers(),
                config.getMaxViewRadius(),
                Objects.requireNonNullElse(play, Duration.ZERO));
    }

    @Override
    public List<ConfigEntry> entries() {
        HytaleServerConfig config = current.get();
        HytaleServerConfig fresh = new HytaleServerConfig();
        BsonDocument encoded = encode(config);
        BsonDocument freshEncoded = encode(fresh);
        List<ConfigEntry> entries = new ArrayList<>();
        BsonValue version = encoded.get("Version");
        if (version != null) {
            entries.add(new ConfigEntry(
                    "Version",
                    GENERAL,
                    "Version",
                    DESCRIPTIONS.get("Version"),
                    ConfigValueKind.SUMMARY,
                    List.of(),
                    0,
                    0,
                    scalar(version),
                    Optional.empty(),
                    SettingAccess.READ_ONLY));
        }
        for (Key key : keys()) {
            try {
                entries.add(entry(key, config, encoded, fresh, freshEncoded));
            } catch (RuntimeException e) {
                log.debug("Skipping server config key " + key.path() + ": " + e);
            }
        }
        return entries;
    }

    @Override
    public synchronized Result<ConfigEntry> write(String path, String value) {
        Optional<Key> found =
                keys().stream().filter(key -> key.path().equals(path)).findFirst();
        if (found.isEmpty()) {
            return Result.failure(Error.notFound("ServerConfig.UnknownKey", "config.json has no key " + path + ".")
                    .localized("controlcore.errors.serverconfig.unknownkey", "path", path));
        }
        Key key = found.get();
        if (accessOf(key) == SettingAccess.READ_ONLY) {
            return Result.failure(Error.forbidden("ServerConfig.ReadOnly", path + " cannot be changed here.")
                    .localized("controlcore.errors.serverconfig.readonly_2", "path", path));
        }
        HytaleServerConfig config = current.get();
        Result<BsonValue> bson = toBson(key, value);
        if (bson.isFailure()) {
            return Result.failure(bson.error());
        }
        Object owner = owner(config, key.segments());
        if (owner == null) {
            return Result.failure(
                    Error.unavailable("ServerConfig.Unavailable", "The section of " + path + " is not loaded.")
                            .localized("controlcore.errors.serverconfig.unavailable", "path", path));
        }
        try {
            if (path.equals(MAX_VIEW_RADIUS)) {
                config.setMaxViewRadius(Integer.parseInt(value.trim()));
            } else {
                decode(key.field(), new BsonDocument(key.last(), bson.value()), owner);
            }
        } catch (RuntimeException e) {
            return Result.failure(Error.validation("ServerConfig.InvalidValue", path + ": " + reason(e))
                    .localized("controlcore.errors.serverconfig.invalidvalue", "path", path, "e", reason(e)));
        }
        config.markChanged();
        if (path.equals(MAX_VIEW_RADIUS)) {
            applyViewRadius(config.getMaxViewRadius());
        }
        if (config.consumeHasChanged()) {
            // One save at a time: Hytale writes config.json.tmp and moves it, so overlapping saves collide.
            saving = saving.thenCompose(done -> HytaleServerConfig.save(config)).exceptionally(failure -> {
                log.error("Could not save the server config", failure);
                return null;
            });
        }
        return Result.success(
                entry(key, config, encode(config), new HytaleServerConfig(), encode(new HytaleServerConfig())));
    }

    // --- Keys ----------------------------------------------------------------------------------------------

    private static List<Key> keys() {
        List<Key> keys = new ArrayList<>();
        walk(HytaleServerConfig.CODEC, List.of(), keys);
        Map<String, Key> unique = new java.util.LinkedHashMap<>();
        keys.forEach(key -> unique.putIfAbsent(key.path(), key));
        List<Key> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingInt(Key::group));
        return sorted;
    }

    private static void walk(BuilderCodec<?> codec, List<String> parent, List<Key> keys) {
        // Fields inherited from a parent codec (for example WorldMap's) come first, as in the file.
        if (codec.getParent() != null) {
            walk(codec.getParent(), parent, keys);
        }
        for (Map.Entry<String, ? extends List<? extends BuilderField<?, ?>>> entry :
                codec.getEntries().entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            BuilderField<?, ?> field = entry.getValue().getLast();
            if (field.isDeprecated() || !field.supportsVersion(HytaleServerConfig.VERSION)) {
                continue;
            }
            List<String> segments = new ArrayList<>(parent);
            segments.add(entry.getKey());
            Codec<?> child = field.getCodec().getChildCodec();
            if (child instanceof BuilderCodec<?> section && segments.size() < MAX_DEPTH) {
                walk(section, List.copyOf(segments), keys);
            } else if (parent.isEmpty()) {
                boolean summary = kindOf(child) == ConfigValueKind.SUMMARY;
                keys.add(new Key(List.copyOf(segments), field, child, summary ? ADVANCED : GENERAL, summary ? 2 : 0));
            } else {
                String section =
                        parent.stream().map(HytaleServerConfiguration::humanize).collect(Collectors.joining(" / "));
                keys.add(new Key(List.copyOf(segments), field, child, section, 1));
            }
        }
    }

    private static ConfigValueKind kindOf(Codec<?> codec) {
        if (codec == Codec.DURATION || codec == Codec.DURATION_SECONDS) {
            return ConfigValueKind.SECONDS;
        }
        return switch (codec) {
            case StringCodec _ -> ConfigValueKind.TEXT;
            case IntegerCodec _, ShortCodec _, ByteCodec _, LongCodec _ -> ConfigValueKind.INTEGER;
            case BooleanCodec _, NullableBooleanCodec _ -> ConfigValueKind.BOOLEAN;
            case EnumCodec<?> _ -> ConfigValueKind.CHOICE;
            default -> ConfigValueKind.SUMMARY;
        };
    }

    private static SettingAccess accessOf(Key key) {
        return kindOf(key.codec()) == ConfigValueKind.SUMMARY
                ? SettingAccess.READ_ONLY
                : HytaleConfigAccess.of(key.path());
    }

    // --- Entries -------------------------------------------------------------------------------------------

    private static ConfigEntry entry(
            Key key,
            HytaleServerConfig config,
            BsonDocument encoded,
            HytaleServerConfig fresh,
            BsonDocument freshEncoded) {
        ConfigValueKind kind = kindOf(key.codec());
        int[] range = range(key, kind);
        List<String> options = key.codec() instanceof EnumCodec<?> choice ? List.of(choice.getEnumKeys()) : List.of();
        String value;
        Optional<String> defaultValue;
        if (key.path().equals(PASSWORD)) {
            String password = config.getPassword();
            value = password == null || password.isEmpty() ? "None (public server)" : "Set (private server)";
            defaultValue = Optional.empty();
        } else if (kind == ConfigValueKind.SUMMARY) {
            value = summary(at(encoded, key.segments()));
            defaultValue = Optional.empty();
        } else {
            value = valueOf(key, kind, config, encoded).orElse("");
            defaultValue = valueOf(key, kind, fresh, freshEncoded);
        }
        String description = Optional.ofNullable(key.field().getDocumentation())
                .filter(text -> !text.isBlank())
                .orElse(DESCRIPTIONS.getOrDefault(key.path(), ""));
        String name = humanize(key.last()) + (kind == ConfigValueKind.SECONDS ? " (seconds)" : "");
        ConfigValueKind shownKind = key.path().equals(PASSWORD) ? ConfigValueKind.SUMMARY : kind;
        return new ConfigEntry(
                key.path(),
                key.section(),
                name,
                description,
                shownKind,
                options,
                range[0],
                range[1],
                value,
                defaultValue,
                key.path().equals(PASSWORD) ? SettingAccess.READ_ONLY : accessOf(key));
    }

    /** The effective value: the getter where one exists (it applies built-in defaults), else the file. */
    private static Optional<String> valueOf(Key key, ConfigValueKind kind, Object root, BsonDocument encoded) {
        Object owner = owner(root, key.segments());
        if (owner != null) {
            Optional<Object> got = getter(owner, key.last());
            if (got.isPresent()) {
                return Optional.of(text(key, kind, got.get()));
            }
        }
        return Optional.ofNullable(at(encoded, key.segments())).map(bson -> text(kind, bson));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String text(Key key, ConfigValueKind kind, Object value) {
        return switch (kind) {
            case SECONDS -> value instanceof Duration duration ? Long.toString(duration.toSeconds()) : value.toString();
            case CHOICE -> {
                try {
                    yield text(kind, ((Codec) key.codec()).encode(value, ExtraInfo.THREAD_LOCAL.get()));
                } catch (RuntimeException e) {
                    yield value.toString();
                }
            }
            default -> value.toString();
        };
    }

    private static String text(ConfigValueKind kind, BsonValue bson) {
        if (kind == ConfigValueKind.SECONDS) {
            if (bson.isString()) {
                return Long.toString(Duration.parse(bson.asString().getValue()).toSeconds());
            }
            if (bson.isNumber()) {
                return Long.toString((long) bson.asNumber().doubleValue());
            }
        }
        return scalar(bson);
    }

    private static String scalar(BsonValue bson) {
        return switch (bson.getBsonType()) {
            case STRING -> bson.asString().getValue();
            case INT32 -> Integer.toString(bson.asInt32().getValue());
            case INT64 -> Long.toString(bson.asInt64().getValue());
            case DOUBLE -> Double.toString(bson.asDouble().getValue());
            case BOOLEAN -> Boolean.toString(bson.asBoolean().getValue());
            case NULL -> "";
            default -> summary(bson);
        };
    }

    /** A one-line description of a structured value, such as {@code Disk (bans.json)} or {@code 2: A, B}. */
    private static String summary(BsonValue bson) {
        if (bson == null || bson.isNull()) {
            return "(not set)";
        }
        if (bson.isDocument()) {
            BsonDocument document = bson.asDocument();
            if (document.isEmpty()) {
                return "(none)";
            }
            if (document.containsKey("Type") && document.get("Type").isString()) {
                String details = document.entrySet().stream()
                        .filter(entry -> !entry.getKey().equals("Type")
                                && !entry.getValue().isDocument())
                        .map(entry -> scalar(entry.getValue()))
                        .collect(Collectors.joining(", "));
                return document.getString("Type").getValue() + (details.isEmpty() ? "" : " (" + details + ")");
            }
            return shorten(document.size() + ": " + String.join(", ", document.keySet()));
        }
        if (bson.isArray()) {
            return bson.asArray().isEmpty()
                    ? "(none)"
                    : shorten(bson.asArray().size() + ": "
                            + bson.asArray().stream()
                                    .map(HytaleServerConfiguration::scalar)
                                    .collect(Collectors.joining(", ")));
        }
        return scalar(bson);
    }

    private static String shorten(String text) {
        return text.length() <= 200 ? text : text.substring(0, 197) + "...";
    }

    private static int[] range(Key key, ConfigValueKind kind) {
        if (kind == ConfigValueKind.SECONDS) {
            return new int[] {1, MAX_SECONDS};
        }
        if (kind != ConfigValueKind.INTEGER) {
            return new int[] {0, 0};
        }
        if (key.path().equals(MAX_VIEW_RADIUS)) {
            return new int[] {1, HytaleServerConfig.MAX_VIEW_RADIUS_LIMIT};
        }
        long min = key.codec() instanceof ShortCodec ? Short.MIN_VALUE : Integer.MIN_VALUE;
        long max = key.codec() instanceof ShortCodec ? Short.MAX_VALUE : Integer.MAX_VALUE;
        if (key.codec() instanceof ByteCodec) {
            min = Byte.MIN_VALUE;
            max = Byte.MAX_VALUE;
        }
        List<? extends Validator<?>> validators = key.field().getValidators();
        if (validators != null) {
            for (Validator<?> validator : validators) {
                if (validator instanceof RangeValidator<?> rangeValidator) {
                    int exclusive = inclusive(rangeValidator) ? 0 : 1;
                    min = bound(rangeValidator, "min", min - exclusive) + exclusive;
                    max = bound(rangeValidator, "max", max + exclusive) - exclusive;
                }
            }
        }
        return new int[] {(int) Math.max(min, Integer.MIN_VALUE), (int) Math.min(max, Integer.MAX_VALUE)};
    }

    private static boolean inclusive(RangeValidator<?> validator) {
        try {
            Field field = RangeValidator.class.getDeclaredField("inclusive");
            field.setAccessible(true);
            return field.getBoolean(validator);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    /** Reads a bound of Hytale's {@link RangeValidator}, which has no getters. */
    private static long bound(RangeValidator<?> validator, String name, long fallback) {
        try {
            Field field = RangeValidator.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(validator) instanceof Number number ? number.longValue() : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    // --- Writing -------------------------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Result<BsonValue> toBson(Key key, String text) {
        String value = text == null ? "" : text.trim();
        ConfigValueKind kind = kindOf(key.codec());
        try {
            return switch (kind) {
                case TEXT -> Result.success(new BsonString(text == null ? "" : text));
                case CHOICE -> Result.success(new BsonString(value));
                case BOOLEAN ->
                    value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                            ? Result.success(BsonBoolean.valueOf(Boolean.parseBoolean(value)))
                            : invalid(key, text);
                case INTEGER -> {
                    long number = Long.parseLong(value);
                    int[] range = range(key, kind);
                    if (number < range[0] || number > range[1]) {
                        yield invalid(key, text);
                    }
                    yield Result.success(
                            key.codec() instanceof LongCodec ? new BsonInt64(number) : new BsonInt32((int) number));
                }
                case SECONDS -> {
                    long seconds = Long.parseLong(value);
                    if (seconds < 1 || seconds > MAX_SECONDS) {
                        yield invalid(key, text);
                    }
                    yield Result.success(
                            key.codec() == Codec.DURATION_SECONDS
                                    ? new BsonDouble(seconds)
                                    : ((Codec) Codec.DURATION)
                                            .encode(Duration.ofSeconds(seconds), ExtraInfo.THREAD_LOCAL.get()));
                }
                case SUMMARY ->
                    Result.failure(Error.forbidden("ServerConfig.ReadOnly", key.path() + " cannot be changed here.")
                            .localized("controlcore.errors.serverconfig.readonly_2", "path", key.path()));
            };
        } catch (NumberFormatException e) {
            return invalid(key, text);
        }
    }

    private static <T> Result<T> invalid(Key key, String text) {
        return Result.failure(
                Error.validation("ServerConfig.InvalidValue", "\"" + text + "\" is not a valid " + key.path() + ".")
                        .localized("controlcore.errors.serverconfig.invalidvalue_2", "text", text, "path", key.path()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void decode(BuilderField<?, ?> field, BsonDocument document, Object owner) {
        ((BuilderField) field).decode(document, owner, ExtraInfo.THREAD_LOCAL.get());
    }

    /** @return the first validation failure in Hytale's exception text, or the message */
    private static String reason(RuntimeException e) {
        String message = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
        return message.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("FAIL:"))
                .map(line -> line.substring("FAIL:".length()).strip())
                .findFirst()
                .orElse(message.lines().findFirst().orElse(message));
    }

    private void applyViewRadius(int radius) {
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (PlayerRef playerRef : world.getPlayerRefs()) {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    Player player = store.getComponent(ref, Player.getComponentType());
                    EntityTrackerSystems.EntityViewer viewer =
                            store.getComponent(ref, EntityTrackerSystems.EntityViewer.getComponentType());
                    if (player != null && viewer != null) {
                        viewer.viewRadiusBlocks = player.getViewRadius() * CHUNK_SIZE;
                        playerRef.getPacketHandler().writeNoCache(new ViewRadius(radius));
                    }
                }
            });
        }
    }

    // --- Navigation ----------------------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BsonDocument encode(HytaleServerConfig config) {
        return ((Codec) HytaleServerConfig.CODEC)
                .encode(config, ExtraInfo.THREAD_LOCAL.get())
                .asDocument();
    }

    private static BsonValue at(BsonDocument document, List<String> segments) {
        BsonValue current = document;
        for (String segment : segments) {
            if (current == null || !current.isDocument()) {
                return null;
            }
            current = current.asDocument().get(segment);
        }
        return current;
    }

    /** @return the object holding the last segment (the config itself or one of its sections), or null */
    private static Object owner(Object root, List<String> segments) {
        Object current = root;
        for (String segment : segments.subList(0, segments.size() - 1)) {
            current = current == null ? null : getter(current, segment).orElse(null);
        }
        return current;
    }

    /** Calls the getter Hytale uses for a key: {@code getX}, {@code isX}, {@code getXConfig}, or without "Timeout". */
    private static Optional<Object> getter(Object target, String key) {
        List<String> names =
                List.of("get" + key, "is" + key, "get" + key + "Config", "get" + key.replaceAll("Timeout$", ""));
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getReturnType() != void.class) {
                    return Optional.ofNullable(method.invoke(target));
                }
            } catch (NoSuchMethodException e) {
                // try the next name
            } catch (ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** {@code MaxViewRadius} → {@code Max view radius}; acronyms such as {@code MOTD} stay as they are. */
    static String humanize(String key) {
        String[] words = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .split(" ");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            boolean acronym = word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT));
            if (i > 0) {
                text.append(' ');
                text.append(acronym ? word : word.toLowerCase(Locale.ROOT));
            } else {
                text.append(word);
            }
        }
        return text.toString();
    }
}
