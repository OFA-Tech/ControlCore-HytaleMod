package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.ServerApi;
import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigValueKind;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import org.junit.jupiter.api.Test;

class MaintenanceStartTest extends RuntimeTestBase {

    @Test
    void aMotdLeftBehindByACrashIsPutBackAtStart() {
        platform.serverConfig.entries.add(new ConfigEntry(
                "MOTD",
                "General",
                "MOTD",
                "",
                ConfigValueKind.TEXT,
                List.of(),
                0,
                0,
                "Closed",
                Optional.empty(),
                SettingAccess.EDITABLE));
        platform.maintenance.state =
                new MaintenanceState(false, "", "Closed", Optional.empty(), Optional.empty(), Optional.of("Welcome"));

        runtime.setup();
        runtime.start();

        assertThat(platform.serverConfig.entry("MOTD").orElseThrow().value()).isEqualTo("Welcome");
        assertThat(platform.maintenance.state.savedMotd()).isEmpty();
    }

    @Test
    void aMotdStillWaitingToBePutBackIsKeptWhenMaintenanceTurnsOnAgain() {
        platform.serverConfig.entries.add(new ConfigEntry(
                "MOTD",
                "General",
                "MOTD",
                "",
                ConfigValueKind.TEXT,
                List.of(),
                0,
                0,
                "Closed",
                Optional.empty(),
                SettingAccess.EDITABLE));
        platform.maintenance.state =
                new MaintenanceState(false, "", "Closed", Optional.empty(), Optional.empty(), Optional.of("Welcome"));
        platform.serverConfig.failWrites = true;
        runtime.setup();
        runtime.start();
        platform.serverConfig.failWrites = false;

        ControlCoreApi.get()
                .server()
                .setMaintenance(true, new ServerApi.MaintenanceOptions("", "Closed again", Optional.empty()));

        assertThat(platform.maintenance.state.savedMotd()).contains("Welcome");
    }
}
