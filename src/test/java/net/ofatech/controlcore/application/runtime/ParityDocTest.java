package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ofatech.controlcore.api.ControlCoreApi;
import org.junit.jupiter.api.Test;

class ParityDocTest extends RuntimeTestBase {

    private static final Path DOC = Path.of("docs", "parity.md");
    private static final Pattern COMMAND = Pattern.compile("`/([a-z][a-z-]*)");
    private static final Pattern API = Pattern.compile("([a-zA-Z]+)\\(\\)\\.([a-zA-Z]+)");

    private static final Set<String> API_ONLY_SECTIONS = Set.of("events", "scheduler", "sessions", "actions");
    private static final Set<String> API_ONLY_METHODS = Set.of("actingFor", "register", "unregister");
    private static final Set<String> API_ONLY = Set.of(
            "settings().setModName",
            "settings().modName",
            "settings().setCategoryPermission",
            "settings().categoryPermission",
            "settings().onAnyChange",
            "players().rights",
            "notifications().message",
            "notifications().broadcast",
            "notifications().toAdmins",
            "notifications().title",
            "notifications().titleAll");

    @Test
    void everyCommandInTheDocIsRegisteredAndEveryRegisteredCommandIsInTheDoc() throws IOException {
        runtime.setup();

        Set<String> documented = column("Command", COMMAND, matcher -> matcher.group(1));
        Set<String> registered = new TreeSet<>(platform.commands.commands.keySet());

        assertThat(registered).isNotEmpty();
        assertThat(documented).as("commands in docs/parity.md").isSubsetOf(registered);
        assertThat(registered).as("registered commands").isSubsetOf(documented);
    }

    @Test
    void everyApiMethodIsInTheDoc() throws IOException {
        Set<String> documented = column("API method", API, matcher -> matcher.group(1) + "()." + matcher.group(2));
        Set<String> methods = new TreeSet<>();
        for (Method section : ControlCoreApi.class.getMethods()) {
            Class<?> type = section.getReturnType();
            if (Modifier.isStatic(section.getModifiers())
                    || !type.isInterface()
                    || !type.getPackageName().equals(ControlCoreApi.class.getPackageName())
                    || API_ONLY_SECTIONS.contains(section.getName())) {
                continue;
            }
            for (Method method : type.getMethods()) {
                String name = section.getName() + "()." + method.getName();
                if (!Modifier.isStatic(method.getModifiers())
                        && !method.isSynthetic()
                        && !API_ONLY_METHODS.contains(method.getName())
                        && !API_ONLY.contains(name)) {
                    methods.add(name);
                }
            }
        }

        assertThat(methods).isNotEmpty();
        assertThat(documented).as("API methods in docs/parity.md").isSubsetOf(methods);
        assertThat(methods).as("API methods").isSubsetOf(documented);
    }

    private static Set<String> column(String header, Pattern pattern, Function<Matcher, String> name)
            throws IOException {
        Set<String> found = new TreeSet<>();
        int index = -1;
        for (String line : Files.readAllLines(DOC)) {
            if (!line.startsWith("|")) {
                index = -1;
                continue;
            }
            List<String> cells =
                    Arrays.stream(line.split("\\|")).map(String::strip).toList();
            if (index < 0) {
                index = cells.indexOf(header);
                continue;
            }
            if (index < cells.size()) {
                Matcher matcher = pattern.matcher(cells.get(index));
                while (matcher.find()) {
                    found.add(name.apply(matcher));
                }
            }
        }
        return found;
    }
}
