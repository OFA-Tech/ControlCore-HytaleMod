package net.ofatech.controlcore.application.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.domain.event.ServerConfigChanged;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.fakes.FakeServerConfiguration;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;

class ServerConfigEditorTest {

    private static final Optional<PlayerId> SERVER_WIDE = Optional.empty();

    private final FakeServerConfiguration server = new FakeServerConfiguration(
            new ServerSettings("My Server", "Hello", false, 20, 12, Duration.ofSeconds(90)));
    private final RecordingLog log = new RecordingLog();
    private final DomainEventBus bus = new DomainEventBus(log);
    private final ServerConfigEditor editor = new ServerConfigEditor(server, bus, bus, log);

    @Test
    void keysBecomeSettingIds() {
        assertThat(ServerConfigEditor.idOf("MOTD")).isEqualTo(SettingId.of("server:motd"));
        assertThat(ServerConfigEditor.idOf("ConnectionTimeouts.PlayTimeout"))
                .isEqualTo(SettingId.of("server:connection-timeouts.play-timeout"));
        assertThat(ServerConfigEditor.idOf("Defaults.GameModeTypeOnDeath"))
                .isEqualTo(SettingId.of("server:defaults.game-mode-type-on-death"));
    }

    @Test
    void listsEveryKeyInFileOrderWithItsAccess() {
        List<SettingSnapshot> snapshots = editor.snapshot(SettingScope.SERVER, SERVER_WIDE);

        assertThat(snapshots)
                .extracting(snapshot -> snapshot.id() + "=" + snapshot.value() + " " + snapshot.access())
                .containsExactly(
                        "server:version=4 READ_ONLY",
                        "server:server-name=My Server EDITABLE",
                        "server:max-view-radius=12 EDITABLE",
                        "server:password=None (public server) READ_ONLY",
                        "server:defaults.game-mode=Creative APPLIES_AFTER_RESTART",
                        "server:connection-timeouts.play-timeout=90 EDITABLE",
                        "server:backup.enabled=false APPLIES_AFTER_RESTART",
                        "server:update.patchline= APPLIES_AFTER_RESTART",
                        "server:ban-storage=Disk (bans.json) READ_ONLY");
        assertThat(snapshots.get(4).definition().type()).isInstanceOf(SettingType.ChoiceType.class);
        assertThat(snapshots.get(4).definition().category()).isEqualTo("Defaults");
        assertThat(snapshots.get(4).definition().description()).contains("Applies after a restart.");
        assertThat(snapshots.get(6).definition().type()).isInstanceOf(SettingType.BooleanType.class);
        assertThat(snapshots.get(6).overridden()).isFalse();
        assertThat(snapshots.get(1).overridden()).isTrue();
        assertThat(editor.snapshot(SettingScope.PLAYER, SERVER_WIDE)).isEmpty();
    }

    @Test
    void writesNormalisedValues() {
        var result = editor.write(id("server:connection-timeouts.play-timeout"), SERVER_WIDE, " 120 ");

        assertThat(result.value().value()).isEqualTo("120");
        assertThat(server.writes).containsExactly("ConnectionTimeouts.PlayTimeout=120");
    }

    @Test
    void refusesInvalidAndReadOnlyChanges() {
        assertThat(editor.write(id("server:max-view-radius"), SERVER_WIDE, "33")
                        .error()
                        .code())
                .isEqualTo("Settings.InvalidValue");
        assertThat(editor.write(id("server:defaults.game-mode"), SERVER_WIDE, "Spectator")
                        .error()
                        .code())
                .isEqualTo("Settings.InvalidValue");
        assertThat(editor.write(id("server:version"), SERVER_WIDE, "5").error().code())
                .isEqualTo("ServerConfig.ReadOnly");
        assertThat(editor.reset(id("server:ban-storage"), SERVER_WIDE).error().code())
                .isEqualTo("ServerConfig.NoDefault");
        assertThat(editor.write(id("server:server-name"), Optional.of(PlayerId.of(UUID.randomUUID())), "x")
                        .error()
                        .code())
                .isEqualTo("Settings.WrongScope");
        assertThat(editor.write(id("server:nope"), SERVER_WIDE, "x").error().code())
                .isEqualTo("Settings.UnknownSetting");
        assertThat(server.writes).isEmpty();
    }

    @Test
    void resetWritesTheValueOfAFreshServer() {
        var result = editor.reset(id("server:server-name"), SERVER_WIDE);

        assertThat(result.value().value()).isEqualTo("Hytale Server");
        assertThat(result.value().overridden()).isFalse();
        assertThat(server.writes).containsExactly("ServerName=Hytale Server");
    }

    @Test
    void keysWithoutADefaultCannotBeReset() {
        assertThat(editor.reset(id("server:update.patchline"), SERVER_WIDE)
                        .error()
                        .code())
                .isEqualTo("ServerConfig.NoDefault");
        assertThat(editor.snapshot(SettingScope.SERVER, SERVER_WIDE)
                        .get(7)
                        .definition()
                        .description())
                .contains("No default value.");
    }

    @Test
    void restartKeysSayWhenTheyApply() {
        var result = editor.write(id("server:defaults.game-mode"), SERVER_WIDE, "Adventure");

        assertThat(result.value().access()).isEqualTo(SettingAccess.APPLIES_AFTER_RESTART);
        assertThat(server.writes).containsExactly("Defaults.GameMode=Adventure");
    }

    @Test
    void changesArePublishedOnceAndSeenByPanelListeners() {
        var published = new ArrayList<ServerConfigChanged>();
        var seen = new ArrayList<SettingChanged>();
        bus.subscribe(ServerConfigChanged.class, published::add);
        editor.onAnyChange(seen::add);

        editor.write(id("server:server-name"), SERVER_WIDE, "Welcome back");
        editor.write(id("server:server-name"), SERVER_WIDE, "Welcome back");

        assertThat(published)
                .containsExactly(new ServerConfigChanged(id("server:server-name"), "My Server", "Welcome back"));
        assertThat(seen).singleElement().satisfies(change -> {
            assertThat(change.scope()).isEqualTo(SettingScope.SERVER);
            assertThat(change.player()).isEmpty();
            assertThat(change.newValue()).isEqualTo("Welcome back");
        });
        assertThat(log.contains("Server config ServerName changed")).isTrue();
    }

    @Test
    void reportsWhenTheServerRejectsTheChange() {
        server.failWrites = true;

        var result = editor.write(id("server:server-name"), SERVER_WIDE, "Welcome back");

        assertThat(result.error().code()).isEqualTo("ServerConfig.InvalidValue");
        assertThat(server.entry("ServerName").orElseThrow().value()).isEqualTo("My Server");
    }

    private static SettingId id(String text) {
        return SettingId.of(text);
    }
}
