package net.ofatech.controlcore.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.application.text.Texts;
import org.junit.jupiter.api.Test;

class KeyDetectLangBanca2Test {

    private static final List<String> KEYS = List.of(
            "controlcore.keys.detect.cancelled",
            "controlcore.keys.detect.locked",
            "controlcore.keys.detect.rights_changed",
            "controlcore.keys.detect.not_saved",
            "controlcore.keys.detect.saved_game",
            "controlcore.keys.detect.unheard",
            "controlcore.ui.keyCapture.holding",
            "controlcore.ui.keyCapture.unsupported");

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void newDetectMessagesExistTranslatedInEveryLanguage() {
        Map<String, String> english = bundled.messages(Texts.ENGLISH);
        for (String language : List.of("en-US", "es-ES", "pt-BR")) {
            Map<String, String> messages = bundled.messages(language);
            for (String key : KEYS) {
                assertThat(messages.get(key)).as(language + " " + key).isNotBlank();
                if (!language.equals(Texts.ENGLISH)) {
                    assertThat(messages.get(key))
                            .as(language + " " + key + " translated")
                            .isNotEqualTo(english.get(key));
                }
            }
        }
    }

    @Test
    void placeholdersTheCodeFillsArePresent() {
        for (String language : List.of("en-US", "es-ES", "pt-BR")) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(messages.get("controlcore.keys.detect.not_saved")).contains("{keys}");
            assertThat(messages.get("controlcore.keys.detect.saved_game")).contains("{keys}");
            assertThat(messages.get("controlcore.ui.keyCapture.holding")).contains("{keys}");
            assertThat(messages.get("controlcore.ui.keyCapture.unsupported")).doesNotContain("{code}");
            assertThat(messages.get("controlcore.keys.detect.none")).contains("{seconds}");
        }
    }
}
