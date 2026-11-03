package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UiSelectorsExistTest {

    private static final Path LAYOUTS = Path.of("src/main/resources/Common/UI/Custom/Pages/ControlCore");
    private static final Path SOURCES =
            Path.of("src/main/java/net/ofatech/controlcore/infrastructure/platforms/hytale");
    private static final Pattern ELEMENT = Pattern.compile("[$\\w.@]+\\s+#(\\w+)\\s*\\{");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern SELECTOR_ID = Pattern.compile("#([A-Z]\\w*)");
    private static final Pattern CONCATENATED = Pattern.compile("\\s*\\+");
    private static final Set<String> FROM_HYTALE_TEMPLATES = Set.of();

    @Test
    void everySelectorIdInJavaIsDeclaredInALayout() throws IOException {
        Set<String> declared = new HashSet<>();
        for (Path layout : files(LAYOUTS, ".ui")) {
            Matcher matcher = ELEMENT.matcher(Files.readString(layout));
            while (matcher.find()) {
                declared.add(matcher.group(1));
            }
        }

        Set<String> missing = new TreeSet<>();
        for (Path source : files(SOURCES, ".java")) {
            String code = Files.readString(source);
            Matcher literal = STRING_LITERAL.matcher(code);
            while (literal.find()) {
                String text = literal.group(1);
                boolean concatenated = CONCATENATED
                        .matcher(code)
                        .region(literal.end(), code.length())
                        .lookingAt();
                Matcher id = SELECTOR_ID.matcher(text);
                while (id.find()) {
                    String name = id.group(1);
                    boolean known = concatenated && id.end() == text.length()
                            ? declared.stream().anyMatch(element -> element.startsWith(name))
                            : declared.contains(name);
                    if (!known && !FROM_HYTALE_TEMPLATES.contains(name)) {
                        missing.add(source.getFileName() + " #" + name);
                    }
                }
            }
        }

        assertThat(declared).isNotEmpty();
        assertThat(missing).isEmpty();
    }

    private static List<Path> files(Path root, String extension) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(extension)).toList();
        }
    }
}
