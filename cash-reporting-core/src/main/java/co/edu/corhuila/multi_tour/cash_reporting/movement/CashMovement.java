package co.edu.corhuila.multi_tour.cash_reporting.movement;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import java.util.Objects;

/** An immutable movement of the day: what kind, through which medium, how much. */
public record CashMovement(MovementType type, PaymentMedium medium, Cents amount) {

    public CashMovement {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(amount, "amount");
        if (amount.value() <= 0) {
            throw new IllegalArgumentException("An amount must be greater than 0");
        }
    }
}
