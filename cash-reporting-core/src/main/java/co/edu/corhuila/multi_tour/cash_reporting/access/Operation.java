package co.edu.corhuila.multi_tour.cash_reporting.access;

/** The operations of the cash in the contract: the daily cash and the daily dashboard and the monthly report, its consolidation and its summary. */
public enum Operation {
    LIST_CASH_DAYS(Kind.READ),
    GET_CURRENT_CASH_DAY(Kind.READ),
    GET_CASH_DAY(Kind.READ),
    LIST_CASH_MOVEMENTS(Kind.READ),
    LIST_CASH_CLOSURES(Kind.READ),
    GET_DAILY_DASHBOARD(Kind.READ),
    LIST_MONTHLY_REPORTS(Kind.READ),
    LIST_MONTHLY_CONSOLIDATIONS(Kind.READ),
    GET_MONTHLY_REPORT_SUMMARY(Kind.READ),
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
