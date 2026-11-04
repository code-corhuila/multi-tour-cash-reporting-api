package co.edu.corhuila.multi_tour.cash_reporting.movement;

/** The kind of a cash movement. The type gives the direction: only {@link #INCOME} is an entry. */
public enum MovementType {
    INCOME,
    OPERATIONAL_PAYMENT,
    OPERATIONAL_EXPENSE,
    REFUND;

    public boolean isEntry() {
        return this == INCOME;
    }
}
