package net.ofatech.controlcore.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ofatech.controlcore.application.text.Texts;
import org.junit.jupiter.api.Test;

class SnapshotAdminLangBanca7Test {

    private static final List<String> LANGUAGES = List.of("en-US", "es-ES", "pt-BR");
    private static final Map<String, Set<String>> KEYS = Map.of(
            "controlcore.snapshots.preview.admin_only", Set.of("count"),
            "controlcore.snapshots.preview.only_admin", Set.of("name"),
            "controlcore.errors.snapshots.admin_parts", Set.of("name"),
            "controlcore.errors.snapshots.admin_parts_save", Set.of(),
            "controlcore.keys.detect.no_panel_anymore", Set.of());
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z]+)}");

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void everyNewKeyIsTranslatedWithTheSamePlaceholdersInEveryLanguage() {
        Map<String, String> english = bundled.messages(Texts.ENGLISH);
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            KEYS.forEach((key, placeholders) -> {
                String text = messages.get(key);
                assertThat(text).as(language + " " + key).isNotBlank();
                assertThat(placeholders(text)).as(language + " " + key).isEqualTo(placeholders);
                if (!language.equals(Texts.ENGLISH)) {
                    assertThat(text).as(language + " " + key).isNotEqualTo(english.get(key));
                }
            });
        }
    }

    @Test
    void theTwoPreviewMessagesAndTheTwoErrorsAreDistinct() {
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(KEYS.keySet().stream().map(messages::get).distinct().count())
                    .as(language)
                    .isEqualTo(KEYS.size());
        }
    }

    @Test
    void errorsMatchTheFallbacksInCode() {
        Map<String, String> english = bundled.messages(Texts.ENGLISH);
        assertThat(english.get("controlcore.errors.snapshots.admin_parts"))
                .isEqualTo(
                        "Only admins can delete, replace or (un)mark as profile '{name}': it holds keys, permissions or mods.");
        assertThat(english.get("controlcore.errors.snapshots.admin_parts_save"))
                .isEqualTo("Leave out Keys, Permissions and Mods: only admins can save them in a snapshot.");
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
