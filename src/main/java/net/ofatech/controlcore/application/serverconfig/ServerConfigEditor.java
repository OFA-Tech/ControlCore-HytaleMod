package net.ofatech.controlcore.application.serverconfig;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.domain.event.ServerConfigChanged;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.ServerSettings;
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
 * The editable fields of the server's own configuration, presented as server settings ({@code server:*} ids) so
 * the control panel edits them like registered settings. The values live in the server config, not in
 * ControlCore's files; "default" means the value a fresh server starts with. Changes are published as
 * {@link ServerConfigChanged}.
 */
public final class ServerConfigEditor implements SettingsEditor {

    static final String CATEGORY = "Hytale server";

    private final ServerConfiguration server;
    private final EventPublisher publisher;
    private final ControlCoreEvents events;
    private final Log log;
    private final List<Field<?>> fields;

    public ServerConfigEditor(ServerConfiguration server, EventPublisher publisher, ControlCoreEvents events, Log log) {
        this.server = server;
        this.publisher = publisher;
        this.events = events;
        this.log = log;
        ServerSettings defaults = server.defaults();
        this.fields = List.of(
                new Field<>(
                        SettingDefinition.server("server:name", SettingType.text(128), defaults.serverName())
                                .named("Server name")
                                .describedAs("Shown to players when they connect and in LAN discovery."),
                        ServerSettings::serverName,
                        ServerSettings::withServerName),
                new Field<>(
                        SettingDefinition.server("server:motd", SettingType.text(256), defaults.motd())
                                .named("Message of the day")
                                .describedAs("Shown to players when they connect."),
                        ServerSettings::motd,
                        ServerSettings::withMotd),
                new Field<>(
                        SettingDefinition.server(
                                        "server:max-players", SettingType.integer(0, 10_000), defaults.maxPlayers())
                                .named("Max players")
                                .describedAs("Players who can be online at once; 0 means unlimited."),
                        ServerSettings::maxPlayers,
                        ServerSettings::withMaxPlayers),
                new Field<>(
                        SettingDefinition.server(
                                        "server:max-view-radius", SettingType.integer(1, 32), defaults.maxViewRadius())
                                .named("Max view radius")
                                .describedAs("View distance limit in chunks; online players are updated at once."),
                        ServerSettings::maxViewRadius,
                        ServerSettings::withMaxViewRadius),
                new Field<>(
                        SettingDefinition.server("server:play-timeout", SettingType.integer(10, 3_600), (int)
                                        defaults.playTimeout().toSeconds())
                                .named("Play timeout")
                                .describedAs("Seconds an unresponsive player stays connected before being dropped."),
                        settings -> (int) settings.playTimeout().toSeconds(),
                        (settings, seconds) -> settings.withPlayTimeout(Duration.ofSeconds(seconds))));
    }

    @Override
    public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
        if (scope != SettingScope.SERVER) {
            return List.of();
        }
        ServerSettings current = server.read();
        return fields.stream().map(field -> field.snapshot(current)).toList();
    }

    @Override
    public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
        return find(id).map(field -> field.snapshot(server.read()));
    }

    @Override
    public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
        if (player.isPresent()) {
            return Result.failure(wrongScope(id));
        }
        return find(id).flatMap(field -> parseAndApply(field, text));
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        if (player.isPresent()) {
            return Result.failure(wrongScope(id));
        }
        return find(id).flatMap(this::applyDefault);
    }

    @Override
    public Subscription onAnyChange(Consumer<? super SettingChanged> listener) {
        return events.subscribe(
                ServerConfigChanged.class,
                change -> listener.accept(new SettingChanged(
                        change.id(), SettingScope.SERVER, Optional.empty(), change.oldValue(), change.newValue())));
    }

    private <T> Result<SettingSnapshot> parseAndApply(Field<T> field, String text) {
        return field.parse(text).flatMap(value -> apply(field, value));
    }

    private <T> Result<SettingSnapshot> applyDefault(Field<T> field) {
        return apply(field, field.definition().defaultValue());
    }

    private <T> Result<SettingSnapshot> apply(Field<T> field, T value) {
        ServerSettings before = server.read();
        String oldValue = field.format(before);
        return server.write(field.with(before, value)).map(after -> {
            SettingSnapshot snapshot = field.snapshot(after);
            if (!snapshot.value().equals(oldValue)) {
                log.info("Server config " + field.definition().id() + " changed from \"" + oldValue + "\" to \""
                        + snapshot.value() + "\"");
                publisher.publish(new ServerConfigChanged(field.definition().id(), oldValue, snapshot.value()));
            }
            return snapshot;
        });
    }

    private Result<Field<?>> find(SettingId id) {
        for (Field<?> field : fields) {
            if (field.definition().id().equals(id)) {
                return Result.success(field);
            }
        }
        return Result.failure(Error.notFound("Settings.UnknownSetting", "There is no server config field " + id + "."));
    }

    private static Error wrongScope(SettingId id) {
        return Error.validation("Settings.WrongScope", id + " is part of the server config, not a player preference.");
    }

    /** One editable value of {@link ServerSettings}. */
    private record Field<T>(
            SettingDefinition<T> definition,
            Function<ServerSettings, T> getter,
            BiFunction<ServerSettings, T, ServerSettings> setter) {

        Field {
            definition = definition.inCategory(CATEGORY);
        }

        Result<T> parse(String text) {
            return definition.type().parse(text);
        }

        String format(ServerSettings settings) {
            return definition.type().format(getter.apply(settings));
        }

        ServerSettings with(ServerSettings settings, T value) {
            return setter.apply(settings, value);
        }

        SettingSnapshot snapshot(ServerSettings settings) {
            String value = format(settings);
            return new SettingSnapshot(definition, value, !value.equals(definition.formattedDefault()));
        }
    }
}
