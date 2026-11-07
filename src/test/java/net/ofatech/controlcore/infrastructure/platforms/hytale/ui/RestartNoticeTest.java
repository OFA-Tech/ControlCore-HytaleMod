package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.fakes.TestTexts;
import org.junit.jupiter.api.Test;

class RestartNoticeTest {

    private final Translator tr = TestTexts.english();

    @Test
    void aFewChangesAreListedAndManyAreCounted() {
        assertThat(SettingRowEditor.pendingText(List.of(), tr)).isEmpty();
        assertThat(SettingRowEditor.pendingText(List.of("A", "B"), tr)).isEqualTo("Restart the server to apply: A, B.");
        assertThat(SettingRowEditor.pendingText(List.of("A", "B", "C", "D", "E"), tr))
                .isEqualTo("5 changes need a restart: A, B and 3 more.");
    }
}
