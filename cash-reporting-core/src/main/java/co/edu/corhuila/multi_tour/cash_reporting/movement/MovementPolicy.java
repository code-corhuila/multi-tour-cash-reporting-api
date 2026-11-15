package co.edu.corhuila.multi_tour.cash_reporting.movement;

/** What a person types by hand. DP-CJ2: an INCOME is accepted; this one method changes if it is forbidden. */
public final class MovementPolicy {

    private MovementPolicy() {}

    public static boolean mayBeTypedByHand(MovementType type) {
        return type != MovementType.REFUND;
    }
}
