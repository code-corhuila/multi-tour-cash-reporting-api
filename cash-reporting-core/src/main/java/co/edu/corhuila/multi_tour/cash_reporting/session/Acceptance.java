package co.edu.corhuila.multi_tour.cash_reporting.session;

/** The decision of a cash about a movement, in the order of the checks of the plan. */
public enum Acceptance {
    ACCEPTED,
    /** A transfer on a closed day is stored flagged as after closing; the day is not reopened. */
    ACCEPTED_AFTER_CLOSING,
    PENDING_CLOSURE,
    NOT_TODAY,
    NO_OPEN_CASH,
    ALREADY_CLOSED;

    public boolean isAccepted() {
        return this == ACCEPTED || this == ACCEPTED_AFTER_CLOSING;
    }
}
