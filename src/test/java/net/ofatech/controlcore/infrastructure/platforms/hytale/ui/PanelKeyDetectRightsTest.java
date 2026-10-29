package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.domain.model.PanelRights;
import org.junit.jupiter.api.Test;

class PanelKeyDetectRightsTest {

    private static final PanelRights ADMIN =
            new PanelRights(true, true, true, true, true, true, true, true, true, true);
    private static final PanelRights PLAYER =
            new PanelRights(true, false, false, false, false, false, false, false, false, false);

    @Test
    void nothingChangedOrOnlyGainedRightsKeepsThePanel() {
        assertThat(PanelKeyDetect.nothingLost(ADMIN, ADMIN)).isTrue();
        assertThat(PanelKeyDetect.nothingLost(PLAYER, ADMIN)).isTrue();
    }

    @Test
    void losingAnyRightTheOldPanelShowsDropsIt() {
        assertThat(PanelKeyDetect.nothingLost(ADMIN, PLAYER)).isFalse();
        assertThat(PanelKeyDetect.nothingLost(
                        ADMIN, new PanelRights(true, true, true, true, true, true, true, true, false, true)))
                .as("lost diagnostics")
                .isFalse();
        assertThat(PanelKeyDetect.nothingLost(
                        ADMIN, new PanelRights(false, true, true, true, true, true, true, true, true, true)))
                .as("admin panel turned off")
                .isFalse();
    }

    @Test
    void turningOffTheAdminPanelDoesNotAffectAPlayersOwnPrefs() {
        PanelRights off = new PanelRights(false, false, false, false, false, false, false, false, false, false);

        assertThat(PanelKeyDetect.nothingLost(PLAYER, off)).isTrue();
    }
}
