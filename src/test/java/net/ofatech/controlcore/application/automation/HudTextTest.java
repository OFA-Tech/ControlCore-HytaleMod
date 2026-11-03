package net.ofatech.controlcore.application.automation;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.application.automation.AutomationService.HudText;
import org.junit.jupiter.api.Test;

class HudTextTest {

    @Test
    void aToastSplitsTheTitleAtTheFirstBar() {
        assertThat(HudText.toast(" Title | body | more ")).isEqualTo(new HudText("Title", "body | more", 0));
        assertThat(HudText.toast("just body")).isEqualTo(new HudText("", "just body", 0));
        assertThat(HudText.toast("Title|").body()).isEmpty();
    }

    @Test
    void aWarningTakesMinutesOnlyFromATrailingNumber() {
        assertThat(HudText.warning("Closing | soon|5")).isEqualTo(new HudText("", "Closing | soon", 5));
        assertThat(HudText.warning("Closing|soon")).isEqualTo(new HudText("", "Closing|soon", 0));
        assertThat(HudText.warning("|12345")).isEqualTo(new HudText("", "|12345", 0));
        assertThat(HudText.warning("|5").body()).isEmpty();
    }
}
