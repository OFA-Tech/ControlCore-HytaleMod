package net.ofatech.controlcore.application.serverconfig;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.api.ControlCoreEvents;
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
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The keys of the server's own configuration file, presented as server settings so the control panel edits them
 * like registered settings. Each key {@code A.BcD} becomes the setting {@code server:a.bc-d}; the list is read
 * from the server every time, so it always matches the file. "Default" writes the value of a fresh server.
 * Read-only keys are refused here as well as by the platform. Changes are published as
 * {@link ServerConfigChanged}.
 */
public final class ServerConfigEditor implements SettingsEditor {

    static final String NAMESPACE = "server";
    private static final int MAX_TEXT = 256;
    private static final int MAX_SUMMARY = 4096;

    private final ServerConfiguration server;
    private final EventPublisher publisher;
    private final ControlCoreEvents events;
    private final Log log;

    public ServerConfigEditor(ServerConfiguration server, EventPublisher publisher, ControlCoreEvents events, Log log) {
        this.server = server;
        this.publisher = publisher;
        this.events = events;
        this.log = log;
    }

    /** @return the setting id of a configuration key, for example {@code server:connection-timeouts.play-timeout} */
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
                        entry.name() + " has no default value; type a value or remove the key from config.json."))));
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
            return Result.failure(Error.forbidden("ServerConfig.ReadOnly", entry.name() + " cannot be changed here."));
        }
        return server.write(entry.path(), value).map(after -> {
            if (!after.value().equals(entry.value())) {
                SettingId id = idOf(entry.path());
                log.info("Server config " + entry.path() + " changed from \"" + entry.value() + "\" to \""
                        + after.value() + "\"");
                publisher.publish(new ServerConfigChanged(id, entry.value(), after.value()));
            }
            return snapshotOf(after);
        });
    }

    private Result<ConfigEntry> find(SettingId id) {
        for (ConfigEntry entry : server.entries()) {
            if (idOf(entry.path()).equals(id)) {
                return Result.success(entry);
            }
        }
        return Result.failure(Error.notFound("Settings.UnknownSetting", "There is no server config key " + id + "."));
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
        return definition.named(entry.name()).describedAs(describe(entry)).inCategory(entry.section());
    }

    private static <T> SettingDefinition<T> server(SettingId id, SettingType<T> type, T defaultValue) {
        return SettingDefinition.server(id.toString(), type, defaultValue);
    }

    private static String describe(ConfigEntry entry) {
        String text = entry.description().isBlank() ? "" : entry.description().strip();
        if (entry.access() == SettingAccess.APPLIES_AFTER_RESTART) {
            text = (text.isEmpty() ? "" : text + " ") + "Applies after a restart.";
        }
        if (entry.defaultValue().isEmpty() && entry.access() != SettingAccess.READ_ONLY) {
            text = (text.isEmpty() ? "" : text + " ") + "No default value.";
        }
        return text;
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
        return Error.validation("Settings.WrongScope", id + " is part of the server config, not a player preference.");
    }
}
