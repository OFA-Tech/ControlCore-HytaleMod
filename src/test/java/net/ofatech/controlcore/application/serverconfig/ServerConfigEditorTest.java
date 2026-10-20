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
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.fakes.FakeServerConfiguration;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;

class ServerConfigEditorTest {

    private static final Optional<PlayerId> SERVER_WIDE = Optional.empty();

    private final FakeServerConfiguration server =
            new FakeServerConfiguration(new ServerSettings("My Server", "Hello", true, 20, 12, Duration.ofSeconds(90)));
    private final RecordingLog log = new RecordingLog();
    private final DomainEventBus bus = new DomainEventBus(log);
    private final ServerConfigEditor editor = new ServerConfigEditor(server, bus, bus, log);

    @Test
    void listsTheEditableFieldsWithTheirCurrentValues() {
        List<SettingSnapshot> snapshots = editor.snapshot(SettingScope.SERVER, SERVER_WIDE);

        assertThat(snapshots)
                .extracting(snapshot -> snapshot.id().toString() + "=" + snapshot.value())
                .containsExactly(
                        "server:name=My Server",
                        "server:motd=Hello",
                        "server:max-players=20",
                        "server:max-view-radius=12",
                        "server:play-timeout=90");
        assertThat(snapshots).allMatch(SettingSnapshot::overridden);
        assertThat(snapshots)
                .allMatch(snapshot -> snapshot.definition().category().equals("Hytale server"));
        assertThat(editor.snapshot(SettingScope.PLAYER, SERVER_WIDE)).isEmpty();
    }

    @Test
    void writesParsedValuesToTheServerConfig() {
        var result = editor.write(id("server:play-timeout"), SERVER_WIDE, " 120 ");

        assertThat(result.value().value()).isEqualTo("120");
        assertThat(server.current.playTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(server.current.passwordProtected()).isTrue();
        assertThat(server.writes).hasSize(1);
    }

    @Test
    void rejectsInvalidValuesWithoutWriting() {
        assertThat(editor.write(id("server:max-view-radius"), SERVER_WIDE, "33")
                        .error()
                        .code())
                .isEqualTo("Settings.InvalidValue");
        assertThat(editor.write(id("server:max-players"), SERVER_WIDE, "lots")
                        .error()
                        .code())
                .isEqualTo("Settings.InvalidValue");
        assertThat(editor.write(id("server:motd"), Optional.of(PlayerId.of(UUID.randomUUID())), "x")
                        .error()
                        .code())
                .isEqualTo("Settings.WrongScope");
        assertThat(editor.write(id("server:nope"), SERVER_WIDE, "x").error().code())
                .isEqualTo("Settings.UnknownSetting");
        assertThat(server.writes).isEmpty();
    }

    @Test
    void resetRestoresTheValueOfAFreshServer() {
        var result = editor.reset(id("server:name"), SERVER_WIDE);

        assertThat(result.value().value()).isEqualTo(FakeServerConfiguration.DEFAULTS.serverName());
        assertThat(result.value().overridden()).isFalse();
        assertThat(server.current.serverName()).isEqualTo("Hytale Server");
    }

    @Test
    void changesArePublishedOnceAndSeenByPanelListeners() {
        var published = new ArrayList<ServerConfigChanged>();
        var seen = new ArrayList<SettingChanged>();
        bus.subscribe(ServerConfigChanged.class, published::add);
        editor.onAnyChange(seen::add);

        editor.write(id("server:motd"), SERVER_WIDE, "Welcome back");
        editor.write(id("server:motd"), SERVER_WIDE, "Welcome back");

        assertThat(published).containsExactly(new ServerConfigChanged(id("server:motd"), "Hello", "Welcome back"));
        assertThat(seen).singleElement().satisfies(change -> {
            assertThat(change.scope()).isEqualTo(SettingScope.SERVER);
            assertThat(change.player()).isEmpty();
            assertThat(change.newValue()).isEqualTo("Welcome back");
        });
        assertThat(log.contains("Server config server:motd changed")).isTrue();
    }

    @Test
    void reportsWhenTheServerRejectsTheChange() {
        server.failWrites = true;

        var result = editor.write(id("server:motd"), SERVER_WIDE, "Welcome back");

        assertThat(result.error().code()).isEqualTo("ServerConfig.WriteFailed");
        assertThat(server.current.motd()).isEqualTo("Hello");
    }

    private static SettingId id(String text) {
        return SettingId.of(text);
    }
}
