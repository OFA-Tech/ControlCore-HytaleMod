package net.ofatech.controlcore.application.controlpanel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.domain.model.ControlPanelRequest;
import net.ofatech.controlcore.domain.model.ModSettings;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.capability.ControlPanelView;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;
import net.ofatech.controlcore.domain.result.ErrorType;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.FakeSettingStore;
import net.ofatech.controlcore.fakes.FakeSettings;
import net.ofatech.controlcore.fakes.RecordingControlPanelView;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.Test;

class ControlPanelServiceTest {

    private static final ServerSettings SERVER =
            new ServerSettings("Caitos Hytale Server", "WELCOME", true, 10, 5, Duration.ofMinutes(10));

    private final FakeSettings settings = new FakeSettings();
    private final FakePlayers players = new FakePlayers();
    private final RecordingControlPanelView builtIn = new RecordingControlPanelView();
    private final RecordingControlPanelView uiMod = new RecordingControlPanelView();
    private final PlayerInfo admin = players.join("Admin");
    private SettingsRegistry registry;

    private ControlPanelService service(Optional<ControlPanelView> capability) {
        Capabilities capabilities = new Capabilities() {
            @Override
            public <T> Optional<T> find(Class<T> type) {
                return type == ControlPanelView.class ? capability.map(type::cast) : Optional.empty();
            }
        };
        RecordingLog log = new RecordingLog();
        DomainEventBus bus = new DomainEventBus(log);
        registry = new SettingsRegistry(new FakeSettingStore(), new FakePlayerData(), bus, bus, log);
        return new ControlPanelService(
                new ControlPanelSettings(registry, settings), players, () -> SERVER, builtIn, capabilities, registry);
    }

    @Test
    void theSwitchCanBeChangedLiveThroughTheRegistry() {
        var service = service(Optional.empty());

        registry.write(SettingId.of(ControlPanelSettings.ENABLED_ID), Optional.empty(), "false");

        assertThat(service.isAvailable()).isFalse();
        assertThat(service.open(admin.id(), ControlPanelRequest.ALL).error().code())
                .isEqualTo("ControlPanel.Disabled");
    }

    @Test
    void usesTheBuiltInViewWithTheServerSettings() {
        var service = service(Optional.empty());

        var result = service.open(admin.id(), ControlPanelRequest.ALL);

        assertThat(result.isSuccess()).isTrue();
        assertThat(builtIn.opened).containsExactly(new RecordingControlPanelView.Opened(admin.id(), SERVER));
        assertThat(builtIn.lastContent.settings()).isSameAs(registry);
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
    void preferencesStayAvailableWhenThePanelIsDisabled() {
        settings.settings = new ModSettings(false);
        var service = service(Optional.empty());

        var result = service.open(admin.id(), ControlPanelRequest.PLAYER_PREFERENCES);

        assertThat(result.isSuccess()).isTrue();
        assertThat(builtIn.lastRequest).isEqualTo(ControlPanelRequest.PLAYER_PREFERENCES);
    }

    @Test
    void narrowRequestsAlwaysUseTheBuiltInView() {
        var service = service(Optional.of(uiMod));

        service.open(admin.id(), ControlPanelRequest.PLAYER_PREFERENCES);

        assertThat(uiMod.opened).isEmpty();
        assertThat(builtIn.players()).containsExactly(admin.id());
    }

    @Test
    void rejectsRequestsWithoutSections() {
        var result = service(Optional.empty()).open(admin.id(), new ControlPanelRequest(false, false, false));

        assertThat(result.error().code()).isEqualTo("ControlPanel.NothingToShow");
        assertThat(builtIn.opened).isEmpty();
    }

    @Test
    void rejectsPlayersWhoAreNotOnline() {
        var result = service(Optional.empty()).open(PlayerId.of(UUID.randomUUID()), ControlPanelRequest.ALL);

        assertThat(result.error().type()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(builtIn.opened).isEmpty();
    }
}
