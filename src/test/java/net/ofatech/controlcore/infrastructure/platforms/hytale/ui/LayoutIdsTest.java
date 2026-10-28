package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LayoutIdsTest {

    private static final Path LAYOUTS = Path.of("src/main/resources/Common/UI/Custom/Pages/ControlCore");
    private static final Pattern ELEMENT = Pattern.compile("(?m)^\\s*[$\\w.@]+\\s+#(\\w+)\\s*\\{");

    @Test
    void everyElementIdIsUsedOncePerLayout() throws IOException {
        List<String> duplicates = new ArrayList<>();
        try (Stream<Path> files = Files.list(LAYOUTS)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".ui")).toList()) {
                Set<String> seen = new HashSet<>();
                Matcher matcher = ELEMENT.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (!seen.add(matcher.group(1))) {
                        duplicates.add(file.getFileName() + " #" + matcher.group(1));
                    }
                }
            }
        }

        assertThat(duplicates).isEmpty();
    }
}
