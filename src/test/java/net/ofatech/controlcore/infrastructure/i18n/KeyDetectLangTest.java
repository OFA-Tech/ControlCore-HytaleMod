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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeyDetectLangTest {

    @Nested
    class DetectMessages {

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
                assertThat(messages.get("controlcore.ui.keyCapture.unsupported"))
                        .doesNotContain("{code}");
                assertThat(messages.get("controlcore.keys.detect.timed_out_here"))
                        .contains("{seconds}");
                assertThat(messages.get("controlcore.keys.detect.screen_timed_out"))
                        .contains("{minutes}");
            }
        }
    }

    @Nested
    class NewKeysAndBusyRemoved {

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
                for (Path file :
                        files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    var matcher = pattern.matcher(Files.readString(file));
                    while (matcher.find()) {
                        used.add(matcher.group(1));
                    }
                }
            }
            assertThat(used).contains("controlcore.keys.detect.gone", "controlcore.keys.detect.not_saved_dead");
            for (String language : LANGUAGES) {
                Map<String, String> messages = bundled.messages(language);
                assertThat(used)
                        .as(language)
                        .allSatisfy(key -> assertThat(messages).containsKey(key));
            }
        }
    }

    @Nested
    class HoldingHint {

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

    @Nested
    class PanelKeyDetectFallbacks {

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
                assertThat(messages.get(SCREEN))
                        .as(language)
                        .contains("{minutes}")
                        .doesNotContain("{seconds}");
                assertThat(messages.get(HERE))
                        .as(language)
                        .contains("{seconds}")
                        .doesNotContain("{minutes}");
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

    @Nested
    class RightsChangedCancel {

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
}
