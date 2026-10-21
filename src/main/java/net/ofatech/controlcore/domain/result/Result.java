package net.ofatech.controlcore.domain.result;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The outcome of an operation that can fail in an expected way: either a value ({@link Success}) or an
 * {@link Error} ({@link Failure}). Use it instead of exceptions for failures a player can cause, such as an
 * unknown name or a value out of range.
 *
 * @param <T> value type; {@link Void} for operations that return nothing
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    /**
     * Creates a successful result holding a value.
     *
     * @param value the value; may be null
     * @param <T> value type
     * @return a {@link Success} with {@code value}
     */
    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a successful result for an operation that returns nothing.
     *
     * @return a {@link Success} whose value is null
     */
    static Result<Void> success() {
        return new Success<>(null);
    }

    /**
     * Creates a failed result.
     *
     * @param error what went wrong; must not be null
     * @param <T> value type the successful result would have had
     * @return a {@link Failure} with {@code error}
     * @throws NullPointerException if {@code error} is null
     */
    static <T> Result<T> failure(Error error) {
        return new Failure<>(error);
    }

    /**
     * Tells whether this result holds a value.
     *
     * @return true for a {@link Success}, false for a {@link Failure}
     */
    boolean isSuccess();

    /**
     * Tells whether this result holds an error.
     *
     * @return true for a {@link Failure}, false for a {@link Success}
     */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the value of a successful result.
     *
     * @return the value; may be null, for example for {@link #success()}
     * @throws IllegalStateException if this is a failure
     */
    T value();

    /**
     * Returns the error of a failed result.
     *
     * @return the error, never null
     * @throws IllegalStateException if this is a success
     */
    Error error();

    /**
     * Transforms the value of a success; a failure is passed on unchanged.
     *
     * @param mapper turns the value into the new value; not called for a failure
     * @param <R> new value type
     * @return a success with the mapped value, or a failure with the same error
     */
    default <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case Success<T> success -> new Success<>(mapper.apply(success.value()));
            case Failure<T> failure -> new Failure<>(failure.error());
        };
    }

    /**
     * Chains another operation that can fail onto a success; a failure is passed on unchanged.
     *
     * @param mapper runs the next operation with the value; not called for a failure
     * @param <R> value type of the next operation
     * @return the result of {@code mapper}, or a failure with the same error
     */
    default <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        return switch (this) {
            case Success<T> success -> mapper.apply(success.value());
            case Failure<T> failure -> new Failure<>(failure.error());
        };
    }

    /**
     * Turns this result into a single value by handling both cases.
     *
     * @param onSuccess called with the value of a success
     * @param onFailure called with the error of a failure
     * @param <R> type of the returned value
     * @return what the called function returned
     */
    default <R> R fold(Function<? super T, ? extends R> onSuccess, Function<Error, ? extends R> onFailure) {
        return switch (this) {
            case Success<T> success -> onSuccess.apply(success.value());
            case Failure<T> failure -> onFailure.apply(failure.error());
        };
    }

    /**
     * Runs an action with the value if this is a success.
     *
     * @param action called with the value; not called for a failure
     * @return this result, for chaining
     */
    default Result<T> onSuccess(Consumer<? super T> action) {
        if (this instanceof Success<T> success) {
            action.accept(success.value());
        }
        return this;
    }

    /**
     * Runs an action with the error if this is a failure.
     *
     * @param action called with the error; not called for a success
     * @return this result, for chaining
     */
    default Result<T> onFailure(Consumer<Error> action) {
        if (this instanceof Failure<T> failure) {
            action.accept(failure.error());
        }
        return this;
    }

    /**
     * Returns the value of a success, or a fallback for a failure.
     *
     * @param fallback returned when this is a failure; may be null
     * @return the value, or {@code fallback}
     */
    default T orElse(T fallback) {
        return isSuccess() ? value() : fallback;
    }

    /**
     * A successful result.
     *
     * @param value the value; may be null
     * @param <T> value type
     */
    record Success<T>(T value) implements Result<T> {

        /**
         * Always true for a success.
         *
         * @return true
         */
        @Override
        public boolean isSuccess() {
            return true;
        }

        /**
         * Always throws: a success has no error.
         *
         * @return never returns
         * @throws IllegalStateException always
         */
        @Override
        public Error error() {
            throw new IllegalStateException("A successful result has no error");
        }
    }

    /**
     * A failed result.
     *
     * @param error what went wrong; never null
     * @param <T> value type the successful result would have had
     */
    record Failure<T>(Error error) implements Result<T> {

        /**
         * Creates a failed result.
         *
         * @param error what went wrong; must not be null
         * @throws NullPointerException if {@code error} is null
         */
        public Failure {
            Objects.requireNonNull(error, "error");
        }

        /**
         * Always false for a failure.
         *
         * @return false
         */
        @Override
        public boolean isSuccess() {
            return false;
        }

        /**
         * Always throws: a failure has no value.
         *
         * @return never returns
         * @throws IllegalStateException always, with the error's code in the message
         */
        @Override
        public T value() {
            throw new IllegalStateException("A failed result has no value: " + error.code());
        }
    }
}
