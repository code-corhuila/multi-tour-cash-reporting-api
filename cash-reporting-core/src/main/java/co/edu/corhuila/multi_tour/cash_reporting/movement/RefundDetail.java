package co.edu.corhuila.multi_tour.cash_reporting.movement;

/** The detail row of a REFUND: the reservation it gives money back for. */
public record RefundDetail(String reservationId) {

    public RefundDetail {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("A refund needs its reservation");
        }
    }
}
