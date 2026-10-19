package net.ofatech.controlcore.domain.result;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The outcome of an operation that can fail in an expected way: either a value or an {@link Error}.
 *
 * @param <T> value type
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static Result<Void> success() {
        return new Success<>(null);
    }

    static <T> Result<T> failure(Error error) {
        return new Failure<>(error);
    }

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * @return the value
     * @throws IllegalStateException if this is a failure
     */
    T value();

    /**
     * @return the error
     * @throws IllegalStateException if this is a success
     */
    Error error();

    default <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case Success<T> success -> new Success<>(mapper.apply(success.value()));
            case Failure<T> failure -> new Failure<>(failure.error());
        };
    }

    default <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        return switch (this) {
            case Success<T> success -> mapper.apply(success.value());
            case Failure<T> failure -> new Failure<>(failure.error());
        };
    }

    default <R> R fold(Function<? super T, ? extends R> onSuccess, Function<Error, ? extends R> onFailure) {
        return switch (this) {
            case Success<T> success -> onSuccess.apply(success.value());
            case Failure<T> failure -> onFailure.apply(failure.error());
        };
    }

    default Result<T> onSuccess(Consumer<? super T> action) {
        if (this instanceof Success<T> success) {
            action.accept(success.value());
        }
        return this;
    }

    default Result<T> onFailure(Consumer<Error> action) {
        if (this instanceof Failure<T> failure) {
            action.accept(failure.error());
        }
        return this;
    }

    default T orElse(T fallback) {
        return isSuccess() ? value() : fallback;
    }

    record Success<T>(T value) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Error error() {
            throw new IllegalStateException("A successful result has no error");
        }
    }

    record Failure<T>(Error error) implements Result<T> {

        public Failure {
            Objects.requireNonNull(error, "error");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T value() {
            throw new IllegalStateException("A failed result has no value: " + error.code());
        }
    }
}
