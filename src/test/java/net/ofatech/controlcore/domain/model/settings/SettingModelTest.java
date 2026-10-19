package net.ofatech.controlcore.domain.model.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.ofatech.controlcore.domain.guard.DomainException;
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
        var definition = SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 3)
                .named("Max homes")
                .describedAs("How many homes")
                .inCategory("Homes");

        assertThat(definition.scope()).isEqualTo(SettingScope.SERVER);
        assertThat(definition.formattedDefault()).isEqualTo("3");
        assertThat(SettingDefinition.player("homes:show", SettingType.bool(), true)
                        .category())
                .isEqualTo("homes");
        assertThatThrownBy(() -> SettingDefinition.server("homes:max-homes", SettingType.integer(1, 50), 99))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("homes:max-homes");
    }
}
