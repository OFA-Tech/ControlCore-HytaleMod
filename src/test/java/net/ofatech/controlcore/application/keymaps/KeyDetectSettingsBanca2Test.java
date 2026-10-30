package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.Test;

class KeyDetectSettingsBanca2Test {

    @Test
    void beforeRegisterTheDefaultsAreUsedWithoutTouchingSettings() {
        KeyDetectSettings settings = new KeyDetectSettings(null);

        assertThat(settings.mode()).isEqualTo(KeyDetection.Mode.GAME);
        assertThat(settings.allKeys()).isFalse();
    }
}
