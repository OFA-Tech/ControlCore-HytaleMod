package net.ofatech.controlcore.application.serverconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.domain.event.ServerConfigChanged;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.game.Worlds;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class ServerConfigEditor implements SettingsEditor {

    public static final String NAMESPACE = "server";
    public static final String WORLD_NAMESPACE = "world";
    private static final int MAX_TEXT = 256;
    private static final int MAX_SUMMARY = 4096;

    private final ServerConfiguration server;
    private final Worlds worlds;
    private final EventPublisher publisher;
    private final ControlCoreEvents events;
    private final Log log;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, String> lastSeen = new ConcurrentHashMap<>();

    private record Pending(String startValue, Text name) {}

    private record Source(SettingId id, ConfigEntry entry, Optional<String> world) {}

    public ServerConfigEditor(
            ServerConfiguration server, Worlds worlds, EventPublisher publisher, ControlCoreEvents events, Log log) {
        this.server = server;
        this.worlds = worlds;
        this.publisher = publisher;
        this.events = events;
        this.log = log;
    }

    public static boolean owns(SettingId id) {
        return NAMESPACE.equals(id.namespace()) || WORLD_NAMESPACE.equals(id.namespace());
    }

    public static boolean isWorld(SettingId id) {
        return WORLD_NAMESPACE.equals(id.namespace());
    }

    public static SettingId worldIdOf(String world, String path) {
        return new SettingId(WORLD_NAMESPACE, slug(world) + "." + idOf(path).key());
    }

    public static String worldPrefix(String world) {
        return slug(world) + ".";
    }

    private static String slug(String world) {
        String slug =
                world.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^[-_]+", "");
        return slug.isEmpty() ? "world" : slug;
    }

    public List<String> worldNames() {
        return worlds.names();
    }

    public Optional<String> worldOf(SettingId id) {
        if (!isWorld(id)) {
            return Optional.empty();
        }
        for (String world : worlds.names()) {
            if (id.key().startsWith(slug(world) + ".")) {
                return Optional.of(world);
            }
        }
        return Optional.empty();
    }

    public void applySavedWorldSettings() {
        worlds.applySaved();
    }

    private List<Source> sources() {
        List<Source> sources = new ArrayList<>();
        server.entries().forEach(entry -> sources.add(new Source(idOf(entry.path()), entry, Optional.empty())));
        for (String world : worlds.names()) {
            for (ConfigEntry entry : worlds.entries(world)) {
                sources.add(new Source(worldIdOf(world, entry.path()), inWorld(world, entry), Optional.of(world)));
            }
        }
        return sources;
    }

    private static ConfigEntry inWorld(String world, ConfigEntry entry) {
        return new ConfigEntry(
                entry.path(),
                world + " · " + entry.section(),
                entry.name(),
                entry.description(),
                entry.kind(),
                entry.options(),
                entry.min(),
                entry.max(),
                entry.value(),
                entry.defaultValue(),
                entry.access());
    }

    public static SettingId idOf(String path) {
        String key = path.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
        return SettingId.of(NAMESPACE + ":" + key);
    }

    @Override
    public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
        if (scope != SettingScope.SERVER) {
            return List.of();
        }
        return sources().stream().map(ServerConfigEditor::snapshotOf).toList();
    }

    @Override
    public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
        return find(id).map(ServerConfigEditor::snapshotOf);
    }

    @Override
    public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
        if (player.isPresent()) {
            return Result.failure(wrongScope(id));
        }
        return find(id).flatMap(source -> {
            SettingDefinition<?> definition = definitionOf(source);
            return normalise(definition, text).flatMap(value -> apply(source, value));
        });
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        if (player.isPresent()) {
            return Result.failure(wrongScope(id));
        }
        return find(id).flatMap(source -> source.entry()
                .defaultValue()
                .map(value -> apply(source, value))
                .orElseGet(() -> Result.failure(Error.validation(
                        "ServerConfig.NoDefault",
                        Text.key(
                                        "controlcore.errors.serverconfig.no_default",
                                        "{name} has no default value; type a value or remove the key from config.json.")
                                .with("name", source.entry().name())))));
    }

    public boolean hasDefault(SettingId id) {
        return find(id).map(source -> source.entry().defaultValue().isPresent()).orElse(false);
    }

    public List<Text> pendingRestart() {
        return pending.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().name())
                .toList();
    }

    @Override
    public Subscription onAnyChange(Consumer<? super SettingChanged> listener) {
        return events.subscribe(
                ServerConfigChanged.class,
                change -> listener.accept(new SettingChanged(
                        change.id(), SettingScope.SERVER, Optional.empty(), change.oldValue(), change.newValue())));
    }

    private Result<SettingSnapshot> apply(Source source, String value) {
        ConfigEntry entry = source.entry();
        if (entry.access() == SettingAccess.READ_ONLY) {
            return Result.failure(Error.forbidden(
                    "ServerConfig.ReadOnly",
                    Text.key("controlcore.errors.serverconfig.read_only", "{name} cannot be changed here.")
                            .with("name", entry.name())));
        }
        Result<ConfigEntry> written = source.world().isPresent()
                ? worlds.write(source.world().get(), entry.path(), value)
                        .map(after -> inWorld(source.world().get(), after))
                : server.write(entry.path(), value);
        return written.map(after -> {
            lastSeen.put(source.id().toString(), after.value());
            if (!after.value().equals(entry.value())) {
                if (entry.access() == SettingAccess.APPLIES_AFTER_RESTART) {
                    Pending first = pending.computeIfAbsent(
                            entry.path(),
                            path -> new Pending(
                                    entry.value(), definitionOf(source).nameText()));
                    if (first.startValue().equals(after.value())) {
                        pending.remove(entry.path());
                    }
                }
                log.info((source.world().map(world -> "World " + world + " ").orElse("Server config ")) + entry.path()
                        + " changed from \"" + entry.value() + "\" to \"" + after.value() + "\"");
                publisher.publish(new ServerConfigChanged(source.id(), entry.value(), after.value()));
            }
            return snapshotOf(new Source(source.id(), after, source.world()));
        });
    }

    public synchronized void checkOutsideChanges() {
        boolean first = lastSeen.isEmpty();
        for (Source source : sources()) {
            ConfigEntry entry = source.entry();
            if (entry.access() == SettingAccess.READ_ONLY) {
                continue;
            }
            String before = lastSeen.put(source.id().toString(), entry.value());
            if (!first && before != null && !before.equals(entry.value())) {
                log.info(source.id() + " changed outside ControlCore from \"" + before + "\" to \"" + entry.value()
                        + "\"");
                ChangeContext.fromOutside(
                        () -> publisher.publish(new ServerConfigChanged(source.id(), before, entry.value())));
            }
        }
    }

    private Result<Source> find(SettingId id) {
        if (owns(id)) {
            for (Source source : sources()) {
                if (source.id().equals(id)) {
                    return Result.success(source);
                }
            }
        }
        return Result.failure(Error.notFound(
                "Settings.UnknownSetting",
                Text.key("controlcore.errors.settings.unknown_config_key", "There is no server config key {id}.")
                        .with("id", id)));
    }

    private static SettingSnapshot snapshotOf(Source source) {
        ConfigEntry entry = source.entry();
        SettingDefinition<?> definition = definitionOf(source);
        boolean overridden =
                entry.defaultValue().map(value -> !value.equals(entry.value())).orElse(true);
        return new SettingSnapshot(definition, entry.value(), overridden, entry.access());
    }

    private static SettingDefinition<?> definitionOf(Source source) {
        ConfigEntry entry = source.entry();
        SettingId id = source.id();
        String fallback = entry.defaultValue().orElse(entry.value());
        SettingDefinition<?> definition = switch (entry.kind()) {
            case TEXT -> server(id, SettingType.text(MAX_TEXT), fallback);
            case SUMMARY -> server(id, SettingType.text(MAX_SUMMARY), fallback);
            case BOOLEAN -> server(id, SettingType.bool(), Boolean.parseBoolean(fallback));
            case CHOICE ->
                server(
                        id,
                        SettingType.choice(entry.options().toArray(String[]::new)),
                        entry.options().contains(fallback)
                                ? fallback
                                : entry.options().getFirst());
            case INTEGER, SECONDS ->
                server(id, SettingType.integer(entry.min(), entry.max()), clamp(fallback, entry.min(), entry.max()));
        };
        return definition
                .named(
                        source.world().isPresent()
                                ? Text.key("controlcore.world.key." + Text.slug(entry.path()), entry.name())
                                : Text.key("controlcore.config.key." + id.key(), entry.name()))
                .describedAs(
                        source.world().isPresent() && !entry.description().isBlank()
                                ? Text.key("controlcore.world.desc." + Text.slug(entry.path()), entry.description())
                                : describe(entry))
                .inCategory(
                        source.world().isPresent()
                                ? Text.key(
                                                "controlcore.world.section."
                                                        + Text.slug(entry.section()
                                                                .substring(entry.section()
                                                                                .indexOf(" · ")
                                                                        + 3)),
                                                entry.section())
                                        .with("world", source.world().get())
                                : Text.key(
                                        "controlcore.config.section." + Text.slug(entry.section()), entry.section()));
    }

    private static <T> SettingDefinition<T> server(SettingId id, SettingType<T> type, T defaultValue) {
        return SettingDefinition.server(id.namespace(), id.key(), type, defaultValue)
                .withModName(Text.of(""));
    }

    private static Text describe(ConfigEntry entry) {
        String doc = entry.description().isBlank() ? "" : entry.description().strip();
        boolean restart = entry.access() == SettingAccess.APPLIES_AFTER_RESTART;
        boolean noDefault = entry.defaultValue().isEmpty() && entry.access() != SettingAccess.READ_ONLY;
        if (!restart && !noDefault) {
            return Text.of(doc);
        }
        String fallback =
                (doc + (restart ? " Applies after a restart." : "") + (noDefault ? " No default value." : "")).strip();
        String key = "controlcore.config.desc." + (restart ? "restart" : "") + (restart && noDefault ? "_" : "")
                + (noDefault ? "no_default" : "");
        return Text.key(key, fallback).with("doc", doc);
    }

    private static int clamp(String text, int min, int max) {
        try {
            return Math.clamp(Long.parseLong(text.trim()), min, max);
        } catch (NumberFormatException e) {
            return Math.clamp(0, min, max);
        }
    }

    private static <T> Result<String> normalise(SettingDefinition<T> definition, String text) {
        return definition.type().parse(text).map(value -> definition.type().format(value));
    }

    private static Error wrongScope(SettingId id) {
        return Error.validation(
                "Settings.WrongScope",
                Text.key(
                                "controlcore.errors.settings.config_not_preference",
                                "{id} is part of the server config, not a player preference.")
                        .with("id", id));
    }
}
