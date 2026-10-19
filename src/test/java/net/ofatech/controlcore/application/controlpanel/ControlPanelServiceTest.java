package net.ofatech.controlcore.application.controlpanel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ModSettings;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;
import net.ofatech.controlcore.domain.result.ErrorType;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.FakeSettings;
import net.ofatech.controlcore.fakes.RecordingControlPanelView;
import org.junit.jupiter.api.Test;

class ControlPanelServiceTest {

    private static final ServerSettings SERVER = new ServerSettings("Caitos Hytale Server", "WELCOME", true, 10, 5);

    private final FakeSettings settings = new FakeSettings();
    private final FakePlayers players = new FakePlayers();
    private final RecordingControlPanelView builtIn = new RecordingControlPanelView();
    private final RecordingControlPanelView uiMod = new RecordingControlPanelView();
    private final PlayerInfo admin = players.join("Admin");

    private ControlPanelService service(Optional<ControlPanelView> capability) {
        Capabilities capabilities = new Capabilities() {
            @Override
            public <T> Optional<T> find(Class<T> type) {
                return type == ControlPanelView.class ? capability.map(type::cast) : Optional.empty();
            }
        };
        return new ControlPanelService(settings, players, () -> SERVER, builtIn, capabilities);
    }

    @Test
    void usesTheBuiltInViewWithTheServerSettings() {
        var service = service(Optional.empty());

        var result = service.open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.isSuccess()).isTrue();
        assertThat(builtIn.opened).containsExactly(new RecordingControlPanelView.Opened(admin.id(), SERVER));
        assertThat(service.isAvailable()).isTrue();
        assertThat(service.usesUiMod()).isFalse();
    }

    @Test
    void prefersAUiModWhenOneIsBound() {
        var service = service(Optional.of(uiMod));

        service.open(admin.id(), ControlPanelRequest.ALL);

        assertThat(uiMod.players()).containsExactly(admin.id());
        assertThat(builtIn.opened).isEmpty();
        assertThat(service.usesUiMod()).isTrue();
    }

    @Test
    void respectsTheSettingsSwitch() {
        settings.settings = new ModSettings(false);
        var service = service(Optional.empty());

        var result = service.open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.error().code()).isEqualTo("ControlPanel.Disabled");
        assertThat(service.isAvailable()).isFalse();
        assertThat(builtIn.opened).isEmpty();
    }

    @Test
    void rejectsPlayersWhoAreNotOnline() {
        var result = service(Optional.empty()).open(PlayerId.of(UUID.randomUUID()), ControlPanelRequest.ALL);

        assertThat(result.error().type()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(builtIn.opened).isEmpty();
    }
}
