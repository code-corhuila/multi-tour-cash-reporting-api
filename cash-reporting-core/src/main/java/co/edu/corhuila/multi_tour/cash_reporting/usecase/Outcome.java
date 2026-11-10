package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import java.util.Objects;

/** The result of a write that carries an Idempotency-Key, and whether it was the stored answer of an earlier call. */
public record Outcome<T>(T value, boolean replayed) {

    public Outcome {
        Objects.requireNonNull(value, "value");
    }

    static <T> Outcome<T> created(T value) {
        return new Outcome<>(value, false);
    }

    static <T> Outcome<T> replay(T value) {
        return new Outcome<>(value, true);
    }
}
