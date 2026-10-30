package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OverlaySizeBanca9Test {

    @Test
    void emptyAndBlankTextCountAsOneLine() {
        assertThat(OverlaySize.lines("", 54)).isEqualTo(1);
        assertThat(OverlaySize.lines("     ", 54)).isEqualTo(1);
        assertThat(OverlaySize.lines("\t \t", 54)).isEqualTo(1);
        assertThat(OverlaySize.toastHeight("", "")).isEqualTo(18 + 17);
        assertThat(OverlaySize.toastHeight("   ", "   ")).isEqualTo(18 + 17);
        assertThat(OverlaySize.warningHeight("")).isEqualTo(34);
        assertThat(OverlaySize.warningHeight("   ")).isEqualTo(34);
    }

    @Test
    void exactWidthStaysOnOneLineAndOneMoreWraps() {
        assertThat(OverlaySize.lines("x".repeat(54), 54)).isEqualTo(1);
        assertThat(OverlaySize.lines("x".repeat(55), 54)).isEqualTo(2);
        assertThat(OverlaySize.lines("x".repeat(108), 54)).isEqualTo(2);
        assertThat(OverlaySize.lines("x".repeat(109), 54)).isEqualTo(3);
    }

    @Test
    void aGiantWordAfterAShortWordWrapsWithoutHanging() {
        int lines = assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> OverlaySize.lines("ab " + "x".repeat(200_000), 54));

        assertThat(lines).isEqualTo(OverlaySize.MAX_LINES);
    }

    @Test
    void emojiDoNotBreakTheCount() {
        String emoji = "\uD83D\uDE00";

        assertThat(OverlaySize.lines(emoji, 54)).isEqualTo(1);
        assertThat(OverlaySize.lines((emoji + " ").repeat(30), 54)).isBetween(2, 4);
        assertThat(OverlaySize.toastHeight(emoji, emoji)).isEqualTo(18 + 19 + 17);
    }

    @Test
    void windowsLineEndsAndBlankParagraphsCount() {
        assertThat(OverlaySize.lines("a\r\nb", 54)).isEqualTo(2);
        assertThat(OverlaySize.lines("a\n\nb", 54)).as("blank lines collapse").isEqualTo(2);
        assertThat(OverlaySize.lines("\n", 54)).isEqualTo(1);
        assertThat(OverlaySize.lines("a\n", 54)).isEqualTo(1);
    }

    @Test
    void manyLineBreaksStayFastAndAreCapped() {
        String text = "a\n".repeat(10_000);

        int lines = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> OverlaySize.lines(text, 54));

        assertThat(lines).isEqualTo(OverlaySize.MAX_LINES);
        assertThat(OverlaySize.warningHeight(text)).isEqualTo(14 + 20 * OverlaySize.MAX_LINES);
    }

    @Test
    void heightsGrowWithTheText() {
        assertThat(OverlaySize.toastHeight("T", "a\nb\nc")).isEqualTo(18 + 19 + 17 * 3);
        assertThat(OverlaySize.warningHeight("a\nb\nc")).isEqualTo(14 + 20 * 3);
        assertThat(OverlaySize.warningHeight("x".repeat(OverlaySize.WARNING_CHARS_PER_LINE + 1)))
                .isEqualTo(54);
    }
}
