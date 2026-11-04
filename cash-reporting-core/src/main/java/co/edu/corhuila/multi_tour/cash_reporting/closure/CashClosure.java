package co.edu.corhuila.multi_tour.cash_reporting.closure;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import java.time.Instant;
import java.util.Objects;

/**
 * The snapshot of a day taken when it is closed: every figure, the entries and exits by medium, the expected cash, the
 * origin of the cash, how it was closed, by whom and when. It is history: it is inserted once and never rewritten.
 *
 * @param view the figures of the day at the moment of closing
 * @param formalClosureOfPending true when a day already over was closed late, keeping its own date
 */
public record CashClosure(
        String id,
        String tenantId,
        BusinessDate date,
        Origin origin,
        ClosingMode mode,
        DayView view,
        String closedBy,
        Instant closedAt,
        boolean formalClosureOfPending) {

    public CashClosure {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(closedAt, "closedAt");
    }

    /** Takes the snapshot of the session with the figures computed for it. */
    public static CashClosure snapshot(
            String id,
            CashSession session,
            DayView view,
            ClosingMode mode,
            String closedBy,
            Instant closedAt,
            boolean formalClosureOfPending) {
        return new CashClosure(
                id,
                session.tenantId(),
                session.date(),
                session.origin(),
                mode,
                view,
                closedBy,
                closedAt,
                formalClosureOfPending);
    }

    /** The closing balance is the total of the day (plan section 3; H-05). */
    public Cents closingBalance() {
        return view.totalCents();
    }
}
