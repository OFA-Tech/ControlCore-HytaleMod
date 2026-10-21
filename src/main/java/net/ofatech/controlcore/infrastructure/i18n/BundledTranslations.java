package net.ofatech.controlcore.infrastructure.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.ports.system.Translations;

public final class BundledTranslations implements Translations {

    public static final String FILE = "controlcore";

    private final ClassLoader loader;
    private final Map<String, Map<String, String>> languages = new ConcurrentHashMap<>();

    public BundledTranslations() {
        this(BundledTranslations.class.getClassLoader());
    }

    public BundledTranslations(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public Optional<String> lookup(String language, String key) {
        return Optional.ofNullable(messages(language).get(key));
    }

    public Map<String, String> messages(String language) {
        return languages.computeIfAbsent(language, this::load);
    }

    private Map<String, String> load(String language) {
        String path = "Server/Languages/" + language + "/" + FILE + ".lang";
        try (InputStream in = loader.getResourceAsStream(path)) {
            return in == null ? Map.of() : parse(in, FILE + ".");
        } catch (IOException e) {
            return Map.of();
        }
    }

    public static Map<String, String> parse(InputStream in, String prefix) throws IOException {
        Map<String, String> messages = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                messages.put(
                        prefix + trimmed.substring(0, equals).strip(),
                        trimmed.substring(equals + 1).strip());
            }
        }
        return Map.copyOf(messages);
    }
}
