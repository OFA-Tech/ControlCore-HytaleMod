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

class SnapshotAdminLangBanca8Test {

    private static final List<String> LANGUAGES = List.of("en-US", "es-ES", "pt-BR");
    private static final Map<String, Set<String>> KEYS = Map.of(
            "controlcore.snapshots.kept_for_admins", Set.of("count"),
            "controlcore.snapshots.replaced", Set.of("name", "parts"),
            "controlcore.profiles.status.admin_only", Set.of("count"));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z]+)}");

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void newKeysExistInEveryLanguageWithTheSamePlaceholders() {
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
    void everyAdminOnlyMessageMentionsMods() {
        Map<String, String> words = Map.of("en-US", "mod", "es-ES", "mod", "pt-BR", "mod");
        List<String> keys = List.of(
                "controlcore.snapshots.kept_for_admins",
                "controlcore.profiles.status.admin_only",
                "controlcore.snapshots.preview.admin_only",
                "controlcore.snapshots.preview.only_admin",
                "controlcore.errors.snapshots.admin_parts",
                "controlcore.errors.snapshots.admin_parts_save");
        for (String language : LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            for (String key : keys) {
                assertThat(messages.get(key).toLowerCase())
                        .as(language + " " + key)
                        .contains(words.get(language));
            }
        }
    }

    @Test
    void statusSuffixIsInParentheses() {
        for (String language : LANGUAGES) {
            String text = bundled.messages(language).get("controlcore.profiles.status.admin_only");
            assertThat(text).as(language).startsWith("(+{count}").endsWith(".)");
        }
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
