package net.ofatech.controlcore.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.ofatech.controlcore.application.text.Texts;
import org.junit.jupiter.api.Test;

class RightsChangedLangBanca6Test {

    private static final List<String> LANGUAGES = List.of("en-US", "es-ES", "pt-BR");
    private static final String CANCELLED = "controlcore.keys.detect.rights_changed_cancelled";
    private static final String CHANGED = "controlcore.keys.detect.rights_changed";

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void cancelledKeyExistsTranslatedAndWithoutPlaceholdersInEveryLanguage() {
        Map<String, String> english = bundled.messages(Texts.ENGLISH);
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(messages.get(CANCELLED)).as(language).isNotBlank().doesNotContain("{");
            assertThat(messages.get(CHANGED)).as(language + " sibling key").isNotBlank();
            assertThat(messages.get(CANCELLED)).as(language).isNotEqualTo(messages.get(CHANGED));
            if (!language.equals(Texts.ENGLISH)) {
                assertThat(messages.get(CANCELLED)).as(language).isNotEqualTo(english.get(CANCELLED));
            }
        }
    }

    @Test
    void englishTextSaysDetectWasCancelledAndToReopenThePanel() {
        assertThat(bundled.messages(Texts.ENGLISH).get(CANCELLED))
                .contains("cancelled")
                .contains("permissions")
                .contains("panel");
    }
}
