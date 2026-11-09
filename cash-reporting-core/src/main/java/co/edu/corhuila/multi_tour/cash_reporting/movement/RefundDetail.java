package co.edu.corhuila.multi_tour.cash_reporting.movement;

/**
 * The detail row of a REFUND: the reservation it gives money back for and, when the saga supplies it, the id of the
 * refund authorization (null until then: the adapter keeps the movement id in its place, TD-18).
 */
public record RefundDetail(String reservationId, String authorizationId) {
    public RefundDetail {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("A refund needs its reservation");
        }
    }

    public RefundDetail(String reservationId) {
        this(reservationId, null);
    }
}
