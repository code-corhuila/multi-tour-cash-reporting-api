package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import java.time.ZoneId;
import java.util.List;

/**
 * What a read answers for a day: the cash when there is one (null for NO_OPENING), the status, the pending mark, the
 * figures computed from the movements and, for a closed day, its latest closure. {@code parametrizedBase} is the base a
 * Collaborator would open with, only while the day has no operational opening (AC-CJ.13); null otherwise.
 */
public record CashDay(
        BusinessDate date,
        ZoneId zone,
        CashSession session,
        DayStatus status,
        boolean pendingClosure,
        DayView figures,
        CashClosure closure,
        Cents parametrizedBase) {

    /** A date without cash: all figures zero, no expected cash. */
    static CashDay noOpening(BusinessDate date, ZoneId zone, Cents parametrizedBase) {
        return new CashDay(date, zone, null, DayStatus.NO_OPENING, false, DayTotals.compute(null, List.of()), null,
                parametrizedBase);
    }

    static CashDay of(CashSession session, List<CashMovement> movements, CashClosure closure, BusinessDate today,
            Cents parametrizedBase) {
        boolean closed = session.status() == SessionStatus.CLOSED;
        var base = session.hasOperationalOpening() ? session.opening().base() : null;
        return new CashDay(
                session.date(),
                session.zone(),
                session,
                closed ? DayStatus.CLOSED : DayStatus.OPEN,
                session.isPending(today),
                DayTotals.compute(base, movements),
                closed ? closure : null,
                session.hasOperationalOpening() ? null : parametrizedBase);
    }
}
