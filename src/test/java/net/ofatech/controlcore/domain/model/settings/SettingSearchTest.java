package net.ofatech.controlcore.domain.model.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettingSearchTest {

    private final SettingSnapshot welcome = new SettingSnapshot(
            SettingDefinition.server("controlcore:welcome-message", SettingType.text(256), "")
                    .named("Welcome message")
                    .inCategory("ControlCore"),
            "Hello {player}!",
            true,
            SettingAccess.EDITABLE);
    private final SettingSnapshot home = new SettingSnapshot(
            SettingDefinition.player("homes:go-home", SettingType.text(200), "F")
                    .named("Go home")
                    .inCategory("homes"),
            "CTRL + F",
            false,
            SettingAccess.EDITABLE);

    @Test
    void plainWordsMatchAnyFieldAndAllMustMatch() {
        assertThat(matches("welcome", welcome)).isTrue();
        assertThat(matches("WELCOME hello", welcome)).isTrue();
        assertThat(matches("welcome goodbye", welcome)).isFalse();
        assertThat(matches("controlcore:welcome", welcome)).isTrue();
        assertThat(matches("sent to players", welcome)).isTrue();
        assertThat(matches("", welcome)).isTrue();
    }

    @Test
    void keysMatchWithoutSpaces() {
        assertThat(matches("ctrl+f", home)).isTrue();
        assertThat(matches("key:ctrl", home)).isTrue();
        assertThat(matches("key:alt", home)).isFalse();
        assertThat(matches("default:f", home)).isTrue();
    }

    @Test
    void prefixesLimitTheField() {
        assertThat(matches("mod:homes", home)).isTrue();
        assertThat(matches("mod:homes", welcome)).isFalse();
        assertThat(matches("name:home", home)).isTrue();
        assertThat(matches("name:homes", home)).isFalse();
        assertThat(matches("id:go-home", home)).isTrue();
        assertThat(matches("cat:controlcore", welcome)).isTrue();
        assertThat(matches("is:changed", welcome)).isTrue();
        assertThat(matches("is:changed", home)).isFalse();
        assertThat(matches("is:default", home)).isTrue();
        assertThat(matches("-mod:homes", home)).isFalse();
        assertThat(matches("-mod:homes", welcome)).isTrue();
        assertThat(matches("unknown:thing", welcome))
                .as("unknown prefixes are plain words")
                .isFalse();
    }

    private static boolean matches(String query, SettingSnapshot snapshot) {
        return SettingSearch.parse(query).matches(snapshot, "Sent to players when they join.");
    }
}
