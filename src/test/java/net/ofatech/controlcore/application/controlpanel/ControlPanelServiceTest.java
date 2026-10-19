package net.ofatech.controlcore.application.controlpanel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ModSettings;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;
import net.ofatech.controlcore.domain.result.ErrorType;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.FakeSettings;
import org.junit.jupiter.api.Test;

class ControlPanelServiceTest {

    private final FakeSettings settings = new FakeSettings();
    private final FakePlayers players = new FakePlayers();
    private final List<PlayerId> opened = new ArrayList<>();
    private final ControlPanelView view = (player, request) -> {
        opened.add(player);
        return Result.success();
    };
    private final PlayerInfo admin = players.join("Admin");

    private ControlPanelService service(Optional<ControlPanelView> ui) {
        Capabilities capabilities = new Capabilities() {
            @Override
            public <T> Optional<T> find(Class<T> capability) {
                return capability == ControlPanelView.class ? ui.map(capability::cast) : Optional.empty();
            }
        };
        return new ControlPanelService(settings, players, capabilities);
    }

    @Test
    void opensThePanelThroughTheUiCapability() {
        var result = service(Optional.of(view)).open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.isSuccess()).isTrue();
        assertThat(opened).containsExactly(admin.id());
        assertThat(service(Optional.of(view)).isAvailable()).isTrue();
    }

    @Test
    void isUnavailableWithoutAUiMod() {
        var result = service(Optional.empty()).open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.error().code()).isEqualTo("ControlPanel.UiUnavailable");
        assertThat(result.error().type()).isEqualTo(ErrorType.UNAVAILABLE);
        assertThat(service(Optional.empty()).isAvailable()).isFalse();
    }

    @Test
    void respectsTheSettingsSwitch() {
        settings.settings = new ModSettings(false);

        var result = service(Optional.of(view)).open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.error().code()).isEqualTo("ControlPanel.Disabled");
        assertThat(service(Optional.of(view)).isAvailable()).isFalse();
        assertThat(opened).isEmpty();
    }

    @Test
    void rejectsPlayersWhoAreNotOnline() {
        var result = service(Optional.of(view)).open(PlayerId.of(java.util.UUID.randomUUID()), ControlPanelRequest.ALL);

        assertThat(result.error().type()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(opened).isEmpty();
    }
}
