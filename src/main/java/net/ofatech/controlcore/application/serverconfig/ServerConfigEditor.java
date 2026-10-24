package net.ofatech.controlcore.application.serverconfig;

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
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class ServerConfigEditor implements SettingsEditor {

    public static final String NAMESPACE = "server";
    private static final int MAX_TEXT = 256;
    private static final int MAX_SUMMARY = 4096;

    private final ServerConfiguration server;
    private final EventPublisher publisher;
    private final ControlCoreEvents events;
    private final Log log;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, String> lastSeen = new ConcurrentHashMap<>();

    private record Pending(String startValue, Text name) {}

    public ServerConfigEditor(ServerConfiguration server, EventPublisher publisher, ControlCoreEvents events, Log log) {
        this.server = server;
        this.publisher = publisher;
        this.events = events;
        this.log = log;
    }

    public static boolean owns(SettingId id) {
        return NAMESPACE.equals(id.namespace());
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
        return server.entries().stream().map(ServerConfigEditor::snapshotOf).toList();
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
        return find(id).flatMap(entry -> {
            SettingDefinition<?> definition = definitionOf(entry);
            return normalise(definition, text).flatMap(value -> apply(entry, value));
        });
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        if (player.isPresent()) {
            return Result.failure(wrongScope(id));
        }
        return find(id).flatMap(entry -> entry.defaultValue()
                .map(value -> apply(entry, value))
                .orElseGet(() -> Result.failure(Error.validation(
                        "ServerConfig.NoDefault",
                        Text.key(
                                        "controlcore.errors.serverconfig.no_default",
                                        "{name} has no default value; type a value or remove the key from config.json.")
                                .with("name", entry.name())))));
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

    private Result<SettingSnapshot> apply(ConfigEntry entry, String value) {
        if (entry.access() == SettingAccess.READ_ONLY) {
            return Result.failure(Error.forbidden(
                    "ServerConfig.ReadOnly",
                    Text.key("controlcore.errors.serverconfig.read_only", "{name} cannot be changed here.")
                            .with("name", entry.name())));
        }
        return server.write(entry.path(), value).map(after -> {
            lastSeen.put(entry.path(), after.value());
            if (!after.value().equals(entry.value())) {
                if (entry.access() == SettingAccess.APPLIES_AFTER_RESTART) {
                    Pending first = pending.computeIfAbsent(
                            entry.path(),
                            path -> new Pending(
                                    entry.value(), definitionOf(entry).nameText()));
                    if (first.startValue().equals(after.value())) {
                        pending.remove(entry.path());
                    }
                }
                SettingId id = idOf(entry.path());
                log.info("Server config " + entry.path() + " changed from \"" + entry.value() + "\" to \""
                        + after.value() + "\"");
                publisher.publish(new ServerConfigChanged(id, entry.value(), after.value()));
            }
            return snapshotOf(after);
        });
    }

    public synchronized void checkOutsideChanges() {
        boolean first = lastSeen.isEmpty();
        for (ConfigEntry entry : server.entries()) {
            if (entry.access() == SettingAccess.READ_ONLY) {
                continue;
            }
            String before = lastSeen.put(entry.path(), entry.value());
            if (!first && before != null && !before.equals(entry.value())) {
                log.info("Server config " + entry.path() + " changed outside ControlCore from \"" + before + "\" to \""
                        + entry.value() + "\"");
                ChangeContext.fromOutside(
                        () -> publisher.publish(new ServerConfigChanged(idOf(entry.path()), before, entry.value())));
            }
        }
    }

    private Result<ConfigEntry> find(SettingId id) {
        for (ConfigEntry entry : server.entries()) {
            if (idOf(entry.path()).equals(id)) {
                return Result.success(entry);
            }
        }
        return Result.failure(Error.notFound(
                "Settings.UnknownSetting",
                Text.key("controlcore.errors.settings.unknown_config_key", "There is no server config key {id}.")
                        .with("id", id)));
    }

    static SettingSnapshot snapshotOf(ConfigEntry entry) {
        SettingDefinition<?> definition = definitionOf(entry);
        boolean overridden =
                entry.defaultValue().map(value -> !value.equals(entry.value())).orElse(true);
        return new SettingSnapshot(definition, entry.value(), overridden, entry.access());
    }

    static SettingDefinition<?> definitionOf(ConfigEntry entry) {
        SettingId id = idOf(entry.path());
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
                .named(Text.key("controlcore.config.key." + id.key(), entry.name()))
                .describedAs(describe(entry))
                .inCategory(Text.key("controlcore.config.section." + Text.slug(entry.section()), entry.section()));
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
