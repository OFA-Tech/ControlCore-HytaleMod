package net.ofatech.controlcore.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.ofatech.controlcore.application.keymaps.KeyDetector;
import net.ofatech.controlcore.application.text.Texts;
import org.junit.jupiter.api.Test;

class KeyDetectLangBanca5Test {

    private static final List<String> LANGUAGES = List.of("en-US", "es-ES", "pt-BR");
    private static final String ADMINS_ONLY = "controlcore.errors.keys.server_only_admins";
    private static final String HERE = "controlcore.keys.detect.timed_out_here";
    private static final String SCREEN = "controlcore.keys.detect.screen_timed_out";
    private static final List<String> REMOVED =
            List.of("controlcore.keys.detect.none", "controlcore.keys.detect.unsaved_dropped");

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void newKeysExistInEveryLanguageAndAreTranslated() {
        Map<String, String> english = bundled.messages(Texts.ENGLISH);
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            for (String key : List.of(ADMINS_ONLY, HERE, SCREEN)) {
                assertThat(messages.get(key)).as(language + " " + key).isNotBlank();
                if (!language.equals(Texts.ENGLISH)) {
                    assertThat(messages.get(key)).as(language + " " + key).isNotEqualTo(english.get(key));
                }
            }
        }
    }

    @Test
    void placeholdersMatchWhatPanelKeyDetectFills() {
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(messages.get(SCREEN)).as(language).contains("{minutes}").doesNotContain("{seconds}");
            assertThat(messages.get(HERE)).as(language).contains("{seconds}").doesNotContain("{minutes}");
            assertThat(messages.get(ADMINS_ONLY)).as(language).doesNotContain("{");
        }
    }

    @Test
    void englishFileMatchesTheFallbackInTheCode() {
        assertThat(bundled.messages(Texts.ENGLISH).get(ADMINS_ONLY))
                .isEqualTo("Only admins can change the server's keys and binds.");
    }

    @Test
    void removedKeysAreGoneFromEveryLanguageAndFromTheCode() throws IOException {
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            for (String key : REMOVED) {
                assertThat(messages).as(language).doesNotContainKey(key);
            }
        }
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            List<Path> users = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> {
                        try {
                            String code = Files.readString(file);
                            return REMOVED.stream().anyMatch(code::contains);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            assertThat(users).isEmpty();
        }
    }

    @Test
    void captureHintPromisesTheSameMinutesAsTheScreenTimeout() {
        long minutes = KeyDetector.SCREEN_TIMEOUT.toMinutes();
        for (String language : LANGUAGES) {
            assertThat(bundled.messages(language).get("controlcore.ui.keyCapture.hint"))
                    .as(language)
                    .contains(minutes + " ");
        }
    }
}
