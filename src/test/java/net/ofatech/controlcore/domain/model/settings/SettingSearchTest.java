package net.ofatech.controlcore.domain.model.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettingSearchTest {

    private final SettingSnapshot welcome = new SettingSnapshot(
            SettingDefinition.server("controlcore", "welcome-message", SettingType.text(256), "")
                    .named("Welcome message")
                    .inCategory("ControlCore"),
            "Hello {player}!",
            true,
            SettingAccess.EDITABLE);
    private final SettingSnapshot home = new SettingSnapshot(
            SettingDefinition.player("homes", "go-home", SettingType.text(200), "F")
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

    @Test
    void theDropdownLimitsPlainWordsOrShowsChangedValues() {
        assertThat(filtered("home", SettingSearch.Filter.NAME, home)).isTrue();
        assertThat(filtered("homes", SettingSearch.Filter.NAME, home)).isFalse();
        assertThat(filtered("homes", SettingSearch.Filter.MOD, home)).isTrue();
        assertThat(filtered("ctrl", SettingSearch.Filter.VALUE, home)).isTrue();
        assertThat(filtered("mod:homes", SettingSearch.Filter.NAME, home))
                .as("prefixes still win")
                .isTrue();
        assertThat(filtered("", SettingSearch.Filter.CHANGED, welcome)).isTrue();
        assertThat(filtered("", SettingSearch.Filter.CHANGED, home)).isFalse();
        assertThat(filtered("", SettingSearch.Filter.UNCHANGED, home)).isTrue();
        assertThat(SettingSearch.parse("", SettingSearch.Filter.CHANGED).isEmpty())
                .isFalse();
        assertThat(SettingSearch.Filter.of("Mod id")).isEqualTo(SettingSearch.Filter.MOD);
        assertThat(SettingSearch.Filter.of(null)).isEqualTo(SettingSearch.Filter.ALL);
    }

    @Test
    void theMatchDropdownComparesWordsAndCanNegate() {
        assertThat(compared("go home", SettingSearch.Filter.NAME, SettingSearch.Match.EQUALS, home))
                .as("spaces are ignored on both sides")
                .isTrue();
        assertThat(compared("go", SettingSearch.Filter.NAME, SettingSearch.Match.EQUALS, home))
                .isFalse();
        assertThat(compared("go", SettingSearch.Filter.NAME, SettingSearch.Match.STARTS_WITH, home))
                .isTrue();
        assertThat(compared("home", SettingSearch.Filter.NAME, SettingSearch.Match.STARTS_WITH, home))
                .isFalse();
        assertThat(compared("+f", SettingSearch.Filter.VALUE, SettingSearch.Match.ENDS_WITH, home))
                .isTrue();
        assertThat(compared("ctrl", SettingSearch.Filter.VALUE, SettingSearch.Match.NOT_CONTAINS, home))
                .isFalse();
        assertThat(compared("alt", SettingSearch.Filter.VALUE, SettingSearch.Match.NOT_CONTAINS, home))
                .isTrue();
        assertThat(compared("homes", SettingSearch.Filter.MOD, SettingSearch.Match.NOT_EQUALS, welcome))
                .isTrue();
        assertThat(compared("go", SettingSearch.Filter.NAME, SettingSearch.Match.NOT_STARTS_WITH, home))
                .isFalse();
        assertThat(compared("home", SettingSearch.Filter.NAME, SettingSearch.Match.NOT_ENDS_WITH, home))
                .isFalse();
        assertThat(compared("-ctrl", SettingSearch.Filter.VALUE, SettingSearch.Match.NOT_CONTAINS, home))
                .as("a minus flips a Doesn't match back")
                .isTrue();
        assertThat(compared("anything", SettingSearch.Filter.CHANGED, SettingSearch.Match.NOT_CONTAINS, welcome))
                .isTrue();
        assertThat(SettingSearch.Match.of("Doesn't end with")).isEqualTo(SettingSearch.Match.NOT_ENDS_WITH);
        assertThat(SettingSearch.Match.of("nonsense")).isEqualTo(SettingSearch.Match.CONTAINS);
    }

    private static boolean compared(
            String query, SettingSearch.Filter filter, SettingSearch.Match match, SettingSnapshot snapshot) {
        return SettingSearch.parse(query, filter, match).matches(snapshot, "Sent to players when they join.");
    }

    private static boolean filtered(String query, SettingSearch.Filter filter, SettingSnapshot snapshot) {
        return SettingSearch.parse(query, filter).matches(snapshot, "Sent to players when they join.");
    }

    private static boolean matches(String query, SettingSnapshot snapshot) {
        return SettingSearch.parse(query).matches(snapshot, "Sent to players when they join.");
    }
}
