package net.ofatech.controlcore.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.application.text.Texts;
import org.junit.jupiter.api.Test;

class KeyDetectLangBanca4Test {

    private static final List<String> LANGUAGES = List.of("en-US", "es-ES", "pt-BR");
    private static final List<String> NEW_KEYS = List.of(
            "controlcore.panel.no_longer_admin",
            "controlcore.keys.detect.timed_out",
            "controlcore.keys.detect.timed_out_here",
            "controlcore.ui.keyCapture.holding");

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void roundThreeKeysExistAndAreTranslated() {
        Map<String, String> english = bundled.messages(Texts.ENGLISH);
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            for (String key : NEW_KEYS) {
                assertThat(messages.get(key)).as(language + " " + key).isNotBlank();
                if (!language.equals(Texts.ENGLISH)) {
                    assertThat(messages.get(key)).as(language + " " + key).isNotEqualTo(english.get(key));
                }
            }
        }
    }

    @Test
    void placeholdersMatchWhatTheCodeFills() {
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(messages.get("controlcore.keys.detect.timed_out"))
                    .as(language)
                    .contains("{seconds}");
            assertThat(messages.get("controlcore.ui.keyCapture.holding"))
                    .as(language)
                    .contains("{keys}");
            assertThat(messages.get("controlcore.panel.no_longer_admin"))
                    .as(language)
                    .doesNotContain("{");
            assertThat(messages.get("controlcore.keys.detect.timed_out_here"))
                    .as(language)
                    .contains("{seconds}")
                    .doesNotContainIgnoringCase("open the panel");
        }
    }

    @Test
    void holdingHintNoLongerClaimsTheKeyIsHeld() {
        assertThat(bundled.messages("en-US").get("controlcore.ui.keyCapture.holding"))
                .doesNotContainIgnoringCase("holding");
        assertThat(bundled.messages("es-ES").get("controlcore.ui.keyCapture.holding"))
                .doesNotContainIgnoringCase("manteniendo");
        assertThat(bundled.messages("pt-BR").get("controlcore.ui.keyCapture.holding"))
                .doesNotContainIgnoringCase("segurando");
    }
}
