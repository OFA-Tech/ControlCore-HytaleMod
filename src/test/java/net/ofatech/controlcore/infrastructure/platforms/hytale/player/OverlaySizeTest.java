package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OverlaySizeTest {

    @Test
    void wrapsByWords() {
        assertThat(OverlaySize.lines("Saved.", 54)).isEqualTo(1);
        assertThat(OverlaySize.lines(
                        "Press the key now (hold Ctrl, Alt, Shift or Space first if you want them). Mouse clicks only"
                                + " count with one of those held. To cancel, wait 10 s.",
                        54))
                .isEqualTo(3);
        assertThat(OverlaySize.lines("a\nb", 54)).isEqualTo(2);
        assertThat(OverlaySize.lines("x".repeat(120), 54)).isEqualTo(3);
    }

    @Test
    void longerToastsAreTaller() {
        int one = OverlaySize.toastHeight("Saved", "Done.");
        int three = OverlaySize.toastHeight("Press the key now", "word ".repeat(30));

        assertThat(three).isGreaterThan(one);
        assertThat(OverlaySize.toastHeight("", "Done.")).isLessThan(one);
        assertThat(OverlaySize.warningHeight("Restart soon")).isEqualTo(34);
    }
}
