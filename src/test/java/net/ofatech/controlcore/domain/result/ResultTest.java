package net.ofatech.controlcore.domain.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResultTest {

    private static final Error NOT_FOUND = Error.notFound("Thing.NotFound", "No thing.");

    @Test
    void successCarriesValue() {
        Result<Integer> result = Result.success(2);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo(2);
        assertThatThrownBy(result::error).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failureCarriesError() {
        Result<Integer> result = Result.failure(NOT_FOUND);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().type()).isEqualTo(ErrorType.NOT_FOUND);
        assertThatThrownBy(result::value)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Thing.NotFound");
    }

    @Test
    void mapAndFlatMapOnlyRunOnSuccess() {
        assertThat(Result.success(2).map(v -> v * 10).value()).isEqualTo(20);
        assertThat(Result.success(2)
                        .flatMap(v -> Result.<Integer>failure(NOT_FOUND))
                        .error())
                .isEqualTo(NOT_FOUND);
        assertThat(Result.<Integer>failure(NOT_FOUND).map(v -> v * 10).error()).isEqualTo(NOT_FOUND);
    }

    @Test
    void foldAndOrElse() {
        assertThat(Result.success("a").fold(v -> v + "!", Error::code)).isEqualTo("a!");
        assertThat(Result.<String>failure(NOT_FOUND).fold(v -> v, Error::code)).isEqualTo("Thing.NotFound");
        assertThat(Result.<String>failure(NOT_FOUND).orElse("fallback")).isEqualTo("fallback");
    }

    @Test
    void callbacksRunForTheMatchingSide() {
        StringBuilder seen = new StringBuilder();
        Result.success("ok").onSuccess(seen::append).onFailure(e -> seen.append("bad"));
        Result.<String>failure(NOT_FOUND).onSuccess(seen::append).onFailure(e -> seen.append(e.code()));

        assertThat(seen).hasToString("okThing.NotFound");
    }
}
