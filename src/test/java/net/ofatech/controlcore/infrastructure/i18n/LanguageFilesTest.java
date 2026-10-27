package net.ofatech.controlcore.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.ofatech.controlcore.application.text.Texts;
import org.junit.jupiter.api.Test;

class LanguageFilesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Pattern KEY_IN_CODE = Pattern.compile("\"(controlcore\\.[a-z0-9_.\\-]*[a-z0-9_])\"");
    private static final Pattern UI_KEY = Pattern.compile("%(controlcore\\.[A-Za-z0-9_.\\-]+);");

    private final BundledTranslations bundled = new BundledTranslations();

    @Test
    void everyLanguageHasEveryEnglishKeyWithTheSamePlaceholders() {
        Map<String, String> english = bundled.messages(Texts.ENGLISH);
        assertThat(english).hasSizeGreaterThan(200);
        for (String language : Texts.LANGUAGES) {
            Map<String, String> messages = bundled.messages(language);
            assertThat(messages.keySet()).as(language + " keys").isEqualTo(english.keySet());
            english.forEach((key, text) -> assertThat(placeholders(messages.get(key)))
                    .as(language + " " + key)
                    .isEqualTo(placeholders(text)));
        }
    }

    @Test
    void everyKeyTheCodeAndLayoutsNameExists() throws IOException {
        Set<String> english = bundled.messages(Texts.ENGLISH).keySet();
        Set<String> used = new TreeSet<>();
        collect(Path.of("src/main/java"), ".java", KEY_IN_CODE, used);
        collect(Path.of("src/main/resources/Common/UI/Custom/Pages/ControlCore"), ".ui", UI_KEY, used);
        used.removeIf(key -> key.endsWith(".")
                || key.equals("controlcore.tabs")
                || key.equals("controlcore.hud.rules")
                || key.equals("controlcore.notifications.corners")
                || key.equals("controlcore.notifications.places"));
        assertThat(used).isNotEmpty();
        assertThat(english).containsAll(used);
    }

    @Test
    void layoutKeysUseOnlyCharactersTheClientParses() throws IOException {
        Set<String> keys = new TreeSet<>();
        collect(
                Path.of("src/main/resources/Common/UI/Custom/Pages/ControlCore"),
                ".ui",
                Pattern.compile("%([^;\\s]*);"),
                keys);
        assertThat(keys).isNotEmpty().allMatch(key -> key.matches("[A-Za-z0-9.]+"), "letters, digits and dots");
    }

    private static Set<String> placeholders(String text) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static void collect(Path root, String extension, Pattern pattern, Set<String> keys) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(extension)).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
    }
}
