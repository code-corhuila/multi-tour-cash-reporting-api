package co.edu.corhuila.multi_tour.cash_reporting.movement;

/** How the money moved. Only {@link #CASH} changes the expected cash. */
public enum PaymentMedium {
    CASH,
    TRANSFER
}
