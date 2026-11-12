package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

/** The status of a reservation as the commercial domain reports it (the names of its events). */
public enum ReservationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    IN_EXECUTION,
    COMPLETED,
    CANCELLED
}
