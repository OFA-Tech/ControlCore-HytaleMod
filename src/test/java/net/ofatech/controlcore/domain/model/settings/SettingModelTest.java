package net.ofatech.controlcore.domain.model.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.model.text.Text;
import org.junit.jupiter.api.Test;

class SettingModelTest {

    @Test
    void idsAreParsedCaseInsensitively() {
        var id = SettingId.parse(" Homes:Max-Homes ").value();

        assertThat(id).isEqualTo(new SettingId("homes", "max-homes"));
        assertThat(id.toString()).isEqualTo("homes:max-homes");
    }

    @Test
    void malformedIdsAreRejected() {
        for (String text : new String[] {null, "", "nocolon", ":key", "ns:", "ns:has space", "n s:key", "ns:a:b"}) {
            assertThat(SettingId.parse(text).error().code())
                    .as(String.valueOf(text))
                    .isEqualTo("Settings.InvalidId");
        }
        assertThatThrownBy(() -> SettingId.of("bad")).isInstanceOf(DomainException.class);
    }

    @Test
    void booleansAcceptCommonSpellings() {
        var type = SettingType.bool();

        assertThat(type.parse("ON").value()).isTrue();
        assertThat(type.parse("no").value()).isFalse();
        assertThat(type.parse("maybe").error().code()).isEqualTo(SettingType.INVALID_VALUE);
    }

    @Test
    void numbersRespectTheirBounds() {
        var integer = SettingType.integer(1, 10);
        var decimal = SettingType.decimal(0, 1);

        assertThat(integer.parse(" 7 ").value()).isEqualTo(7);
        assertThat(integer.parse("11").isFailure()).isTrue();
        assertThat(integer.parse("1.5").isFailure()).isTrue();
        assertThat(decimal.parse("0.25").value()).isEqualTo(0.25);
        assertThat(decimal.parse("NaN").isFailure()).isTrue();
        assertThat(decimal.parse("2").isFailure()).isTrue();
        assertThatThrownBy(() -> SettingType.integer(5, 1)).isInstanceOf(DomainException.class);
    }

    @Test
    void textAndChoicesAreValidated() {
        var text = SettingType.text(5);
        var choice = SettingType.choice("Easy", "Hard");

        assertThat(text.parse("hello").value()).isEqualTo("hello");
        assertThat(text.parse("too long").isFailure()).isTrue();
        assertThat(choice.parse("hard").value()).isEqualTo("Hard");
        assertThat(choice.parse("medium").error().description()).contains("one of Easy, Hard");
        assertThat(choice.validate("hard").isFailure()).isTrue();
    }

    @Test
    void definitionsCheckTheirDefault() {
        var definition = SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3)
                .named("Max homes")
                .describedAs("How many homes")
                .inCategory("Homes");

        assertThat(definition.scope()).isEqualTo(SettingScope.SERVER);
        assertThat(definition.formattedDefault()).isEqualTo("3");
        var plain = SettingDefinition.player("home_teleports", "show", SettingType.bool(), true);
        assertThat(plain.id().toString()).isEqualTo("home_teleports:show");
        assertThat(plain.category()).as("no category of its own").isEmpty();
        assertThat(plain.heading()).as("the mod id capitalised").isEqualTo("Home Teleports");
        assertThat(definition.inCategory("Limits").heading()).isEqualTo("Homes: Limits");
        assertThat(definition.withModName(Text.of("")).heading()).isEqualTo("Homes");
        assertThatThrownBy(() -> SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 99))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("homes:max-homes");
    }

    @Test
    void coloursAndTextListsParseAndFormat() {
        var color = SettingType.color();
        assertThat(color.parse("#A1B2C3").value()).isEqualTo("#a1b2c3");
        assertThat(color.parse("fa0").value()).isEqualTo("#ffaa00");
        assertThat(color.parse("red").isFailure()).isTrue();
        assertThat(color.validate("#FFFFFF").isFailure())
                .as("stored lower case")
                .isTrue();

        var list = SettingType.textList(3, 8);
        assertThat(list.parse(" Steve, alex ,, Notch").value()).containsExactly("Steve", "alex", "Notch");
        assertThat(list.format(List.of("a", "b"))).isEqualTo("a, b");
        assertThat(list.parse("a, b, c, d").isFailure()).as("too many").isTrue();
        assertThat(list.parse("waytoolongname").isFailure()).isTrue();
        assertThat(list.parse("").value()).isEmpty();
        var definition = SettingDefinition.server("homes", "allowed", list, List.of("Steve"));
        assertThat(definition.formattedDefault()).isEqualTo("Steve");
    }
}
