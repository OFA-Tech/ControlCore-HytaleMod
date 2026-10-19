package net.ofatech.controlcore.application.settings;

import com.google.auto.service.AutoService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.ports.config.SettingStore;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The shared settings registry: definitions in memory, server values in the {@link SettingStore}, player values
 * in {@link PlayerData} under {@code setting:<id>}. Changes are published as {@link SettingChanged}.
 */
@AutoService(SettingsApi.class)
public final class SettingsRegistry implements SettingsApi {

    static final String PLAYER_KEY_PREFIX = "setting:";

    private static final Comparator<SettingSnapshot> DISPLAY_ORDER = Comparator.comparing(
                    (SettingSnapshot snapshot) -> snapshot.definition().category())
            .thenComparing(SettingSnapshot::id);

    private final SettingStore store;
    private final PlayerData playerData;
    private final EventPublisher publisher;
    private final ControlCoreEvents events;
    private final Log log;
    private final Map<SettingId, SettingDefinition<?>> definitions = new ConcurrentHashMap<>();
    private final Set<String> reportedInvalid = ConcurrentHashMap.newKeySet();

    public SettingsRegistry(
            SettingStore store, PlayerData playerData, EventPublisher publisher, ControlCoreEvents events, Log log) {
        this.store = store;
        this.playerData = playerData;
        this.publisher = publisher;
        this.events = events;
        this.log = log;
    }

    @Override
    public <T> Result<Setting<T>> register(SettingDefinition<T> definition) {
        Objects.requireNonNull(definition, "definition");
        SettingDefinition<?> existing = definitions.putIfAbsent(definition.id(), definition);
        if (existing != null) {
            if (existing.scope() != definition.scope()
                    || existing.type().valueType() != definition.type().valueType()) {
                return Result.failure(Error.conflict(
                        "Settings.Conflict",
                        definition.id() + " is already registered as a " + describe(existing) + "."));
            }
            definitions.put(definition.id(), definition);
            log.debug("Setting " + definition.id() + " re-registered");
        } else {
            log.debug("Setting " + definition.id() + " registered");
        }
        return Result.success(new Handle<>(definition.id(), definition.type().valueType()));
    }

    @Override
    public Optional<SettingDefinition<?>> definition(SettingId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public List<SettingDefinition<?>> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(SettingDefinition::id))
                .<SettingDefinition<?>>map(definition -> definition)
                .toList();
    }

    @Override
    public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
        return definitions.values().stream()
                .filter(definition -> definition.scope() == scope)
                .map(definition -> snapshotOf(definition, player))
                .sorted(DISPLAY_ORDER)
                .toList();
    }

    @Override
    public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
        return find(id).map(definition -> snapshotOf(definition, player));
    }

    @Override
    public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
        return find(id).flatMap(definition -> writeText(definition, player, text));
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        return find(id).flatMap(definition -> checkScope(definition, player).map(ignored -> {
            String before = effective(definition, player);
            if (player.isPresent()) {
                playerData.remove(player.get(), PLAYER_KEY_PREFIX + id);
            } else {
                store.remove(id);
                store.flush();
            }
            reportedInvalid.remove(reportKey(id, player));
            changed(definition, player, before);
            return snapshotOf(definition, player);
        }));
    }

    private Result<SettingDefinition<?>> find(SettingId id) {
        SettingDefinition<?> definition = definitions.get(id);
        return definition != null
                ? Result.success(definition)
                : Result.failure(Error.notFound("Settings.UnknownSetting", "There is no setting " + id + "."));
    }

    private <T> Result<SettingSnapshot> writeText(
            SettingDefinition<T> definition, Optional<PlayerId> player, String text) {
        return checkScope(definition, player)
                .flatMap(ignored -> definition.type().parse(text))
                .map(value -> store(definition, player, definition.type().format(value)));
    }

    private <T> Result<Void> writeValue(SettingDefinition<T> definition, Optional<PlayerId> player, T value) {
        return checkScope(definition, player)
                .flatMap(ignored -> definition.type().validate(value))
                .map(valid -> {
                    store(definition, player, definition.type().format(valid));
                    return null;
                });
    }

    private SettingSnapshot store(SettingDefinition<?> definition, Optional<PlayerId> player, String text) {
        String before = effective(definition, player);
        if (player.isPresent()) {
            playerData.put(player.get(), PLAYER_KEY_PREFIX + definition.id(), text);
        } else {
            store.put(definition.id(), text);
            store.flush();
        }
        reportedInvalid.remove(reportKey(definition.id(), player));
        changed(definition, player, before);
        return snapshotOf(definition, player);
    }

    private void changed(SettingDefinition<?> definition, Optional<PlayerId> player, String before) {
        String after = effective(definition, player);
        if (!after.equals(before)) {
            publisher.publish(new SettingChanged(definition.id(), definition.scope(), player, before, after));
        }
    }

    private static Result<Void> checkScope(SettingDefinition<?> definition, Optional<PlayerId> player) {
        if (definition.scope() == SettingScope.SERVER && player.isPresent()) {
            return Result.failure(Error.validation(
                    "Settings.WrongScope", definition.id() + " is a server setting, not a player preference."));
        }
        if (definition.scope() == SettingScope.PLAYER && player.isEmpty()) {
            return Result.failure(Error.validation(
                    "Settings.WrongScope", definition.id() + " is a player preference; each player sets their own."));
        }
        return Result.success();
    }

    private SettingSnapshot snapshotOf(SettingDefinition<?> definition, Optional<PlayerId> player) {
        boolean overridden = stored(definition, player)
                .map(text -> definition.type().parse(text).isSuccess())
                .orElse(false);
        return new SettingSnapshot(definition, effective(definition, player), overridden);
    }

    private <T> String effective(SettingDefinition<T> definition, Optional<PlayerId> player) {
        return definition.type().format(typed(definition, player));
    }

    private <T> T typed(SettingDefinition<T> definition, Optional<PlayerId> player) {
        Optional<String> stored = stored(definition, player);
        if (stored.isEmpty()) {
            return definition.defaultValue();
        }
        Result<T> parsed = definition.type().parse(stored.get());
        if (parsed.isFailure()) {
            if (reportedInvalid.add(reportKey(definition.id(), player))) {
                log.warn("Stored value '" + stored.get() + "' of " + definition.id()
                        + player.map(id -> " for player " + id).orElse("") + " is not a valid "
                        + definition.type().describe() + "; using the default " + definition.formattedDefault());
            }
            return definition.defaultValue();
        }
        return parsed.value();
    }

    private Optional<String> stored(SettingDefinition<?> definition, Optional<PlayerId> player) {
        if (definition.scope() == SettingScope.PLAYER) {
            return player.flatMap(id -> playerData.get(id, PLAYER_KEY_PREFIX + definition.id()));
        }
        return store.get(definition.id());
    }

    private static String reportKey(SettingId id, Optional<PlayerId> player) {
        return id + player.map(value -> "@" + value).orElse("");
    }

    private static String describe(SettingDefinition<?> definition) {
        return definition.scope().name().toLowerCase(Locale.ROOT) + " "
                + definition.type().describe();
    }

    /** Handle given to the registering mod; always reads the current definition. */
    private final class Handle<T> implements Setting<T> {

        private final SettingId id;
        private final Class<T> valueType;

        Handle(SettingId id, Class<T> valueType) {
            this.id = id;
            this.valueType = valueType;
        }

        @Override
        @SuppressWarnings("unchecked") // register() only replaces a definition with one of the same value type
        public SettingDefinition<T> definition() {
            SettingDefinition<?> current = definitions.get(id);
            if (current == null || current.type().valueType() != valueType) {
                throw new IllegalStateException("Setting " + id + " is no longer registered");
            }
            return (SettingDefinition<T>) current;
        }

        @Override
        public T get() {
            return typed(definition(), Optional.empty());
        }

        @Override
        public T get(PlayerId player) {
            SettingDefinition<T> definition = definition();
            return typed(
                    definition, definition.scope() == SettingScope.PLAYER ? Optional.of(player) : Optional.empty());
        }

        @Override
        public Result<Void> set(T value) {
            return writeValue(definition(), Optional.empty(), value);
        }

        @Override
        public Result<Void> set(PlayerId player, T value) {
            return writeValue(definition(), Optional.of(player), value);
        }

        @Override
        public Result<Void> reset() {
            return SettingsRegistry.this.reset(id, Optional.empty()).map(snapshot -> null);
        }

        @Override
        public Result<Void> reset(PlayerId player) {
            return SettingsRegistry.this.reset(id, Optional.of(player)).map(snapshot -> null);
        }

        @Override
        public Subscription onChange(Consumer<? super SettingChanged> listener) {
            return events.subscribe(SettingChanged.class, event -> {
                if (event.id().equals(id)) {
                    listener.accept(event);
                }
            });
        }

        @Override
        public String toString() {
            return "Setting[" + id + "]";
        }
    }
}
