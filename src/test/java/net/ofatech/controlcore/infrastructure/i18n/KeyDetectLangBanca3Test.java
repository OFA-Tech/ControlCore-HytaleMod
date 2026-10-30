package net.ofatech.controlcore.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.ofatech.controlcore.application.text.Texts;
import org.junit.jupiter.api.Test;

class KeyDetectLangBanca3Test {

    private static final List<String> LANGUAGES = List.of("en-US", "es-ES", "pt-BR");
    private static final List<String> NEW_KEYS = List.of(
            "controlcore.keys.detect.gone",
            "controlcore.keys.detect.also_used",
            "controlcore.keys.detect.not_saved_dead",
            "controlcore.keys.detect.not_saved_gone");

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void newKeysExistAndAreTranslatedInEveryLanguage() {
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
    void placeholdersFilledByTheCodeArePresent() {
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(messages.get("controlcore.keys.detect.also_used"))
                    .as(language)
                    .contains("{actions}");
            assertThat(messages.get("controlcore.keys.detect.not_saved_dead"))
                    .as(language)
                    .contains("{keys}");
            assertThat(messages.get("controlcore.keys.detect.not_saved_gone"))
                    .as(language)
                    .contains("{keys}");
            assertThat(messages.get("controlcore.keys.detect.gone"))
                    .as(language)
                    .doesNotContain("{");
            assertThat(messages.get("controlcore.keys.detect.toast_text"))
                    .as(language)
                    .contains("{seconds}");
        }
    }

    @Test
    void busyIsGoneFromEveryLanguage() {
        for (String language : LANGUAGES) {
            assertThat(bundled.messages(language)).as(language).doesNotContainKey("controlcore.keys.detect.busy");
        }
    }

    @Test
    void everyDetectKeyUsedInTheCodeExistsInEveryLanguage() throws IOException {
        java.util.Set<String> used = new java.util.TreeSet<>();
        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("\"(controlcore\\.(keys\\.detect|ui\\.keyCapture)\\.[a-z_]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) {
                    used.add(matcher.group(1));
                }
            }
        }
        assertThat(used).contains("controlcore.keys.detect.gone", "controlcore.keys.detect.not_saved_dead");
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(used).as(language).allSatisfy(key -> assertThat(messages).containsKey(key));
        }
    }
}
