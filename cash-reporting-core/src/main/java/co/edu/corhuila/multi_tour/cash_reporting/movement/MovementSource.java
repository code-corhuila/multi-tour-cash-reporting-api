package co.edu.corhuila.multi_tour.cash_reporting.movement;

/** Who asks for the movement: a person through the API, or a saga step through the internal use case. */
public enum MovementSource {
    MANUAL,
    SAGA
}
