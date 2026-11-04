package co.edu.corhuila.multi_tour.cash_reporting.access;

/** The operations of the daily cash in the contract (the dashboard and the reports belong to other stories). */
public enum Operation {
    LIST_CASH_DAYS(Kind.READ),
    GET_CURRENT_CASH_DAY(Kind.READ),
    GET_CASH_DAY(Kind.READ),
    LIST_CASH_MOVEMENTS(Kind.READ),
    LIST_CASH_CLOSURES(Kind.READ),
    OPEN_CASH_DAY(Kind.OPERATE),
    CLOSE_CASH_DAY(Kind.OPERATE),
    REOPEN_CASH_DAY(Kind.OPERATE),
    REGISTER_CASH_MOVEMENT(Kind.OPERATE),
    ADJUST_CASH_BASE(Kind.ADMINISTER),
    REGISTER_CASH_CORRECTION(Kind.ADMINISTER);

    /** Who may do it: any tenant role, the administrator or a collaborator with the permission, the administrator. */
    enum Kind {
        READ,
        OPERATE,
        ADMINISTER
    }

    private final Kind kind;

    Operation(Kind kind) {
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
