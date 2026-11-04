package co.edu.corhuila.multi_tour.cash_reporting.session;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The cash of one day of a tenant. It owns its transitions and decides which movements it accepts. The totals are not
 * stored here: they are a pure function of the movements and the base. Immutable: every transition returns a new value.
 *
 * @param opening the operational opening, or null while the cash was created automatically and never opened
 * @param closingMode how it was closed, or null while open
 * @param storedPendingMark the pending mark written by the time job; the derived state is OR-ed with it
 */
public record CashSession(
        String tenantId,
        BusinessDate date,
        ZoneId zone,
        Origin origin,
        Opening opening,
        SessionStatus status,
        ClosingMode closingMode,
        boolean storedPendingMark) {

    /** The operational opening: the base counted, when and by whom. */
    public record Opening(Cents base, Instant at, String by) {

        public Opening {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(by, "by");
        }
    }

    public CashSession {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(status, "status");
    }

    /** A cash opened by the operator: only today can be opened. */
    public static CashSession openNew(
            String tenantId, BusinessDate date, ZoneId zone, Opening opening, BusinessDate today) {
        requireToday(date, today);
        Objects.requireNonNull(opening, "opening");
        return new CashSession(
                tenantId, date, zone, Origin.OPERATIONAL_OPENING, opening, SessionStatus.OPEN, null, false);
    }

    /** The cash a transfer creates on a day without one: no base, no opening. */
    public static CashSession automatic(String tenantId, BusinessDate date, ZoneId zone) {
        return new CashSession(tenantId, date, zone, Origin.AUTOMATIC_CREATION, null, SessionStatus.OPEN, null, false);
    }

    public boolean hasOperationalOpening() {
        return opening != null;
    }

    /** Derived: open, with operational opening and its day already over; OR-ed with the stored mark. */
    public boolean isPending(BusinessDate today) {
        if (status == SessionStatus.CLOSED || !hasOperationalOpening()) {
            return false;
        }
        return storedPendingMark || date.isBefore(today);
    }

    /** Opens a cash created automatically; the origin stays AUTOMATIC_CREATION. */
    public CashSession open(Opening newOpening, BusinessDate today) {
        requireToday(date, today);
        Objects.requireNonNull(newOpening, "opening");
        if (status == SessionStatus.CLOSED) {
            throw new IllegalStateException("The cash is already closed");
        }
        if (hasOperationalOpening()) {
            throw new IllegalStateException("The cash is already open");
        }
        return new CashSession(tenantId, date, zone, origin, newOpening, status, closingMode, storedPendingMark);
    }

    public CashSession adjustBase(Cents newBase) {
        requireOpenWithOpening();
        return new CashSession(
                tenantId,
                date,
                zone,
                origin,
                new Opening(newBase, opening.at(), opening.by()),
                status,
                closingMode,
                storedPendingMark);
    }

    /** Closing clears the stored pending mark. */
    public CashSession close(ClosingMode mode) {
        requireOpenWithOpening();
        return new CashSession(tenantId, date, zone, origin, opening, SessionStatus.CLOSED, mode, false);
    }

    /** An automatic closing is final: only a manual one can be reopened. */
    public CashSession reopen() {
        if (status != SessionStatus.CLOSED) {
            throw new IllegalStateException("The cash is already open");
        }
        if (closingMode == ClosingMode.AUTOMATIC) {
            throw new IllegalStateException("A cash closed automatically cannot be reopened");
        }
        return new CashSession(tenantId, date, zone, origin, opening, SessionStatus.OPEN, null, storedPendingMark);
    }

    public CashSession withStoredPendingMark(boolean mark) {
        return new CashSession(tenantId, date, zone, origin, opening, status, closingMode, mark);
    }

    /** Order of the checks: pending, today, then the rule of the medium. */
    public Acceptance accepts(CashMovement movement, BusinessDate today) {
        if (isPending(today)) {
            return Acceptance.PENDING_CLOSURE;
        }
        if (!date.equals(today)) {
            return Acceptance.NOT_TODAY;
        }
        boolean closed = status == SessionStatus.CLOSED;
        if (movement.medium() == PaymentMedium.CASH) {
            if (!hasOperationalOpening()) {
                return Acceptance.NO_OPEN_CASH;
            }
            return closed ? Acceptance.ALREADY_CLOSED : Acceptance.ACCEPTED;
        }
        return closed ? Acceptance.ACCEPTED_AFTER_CLOSING : Acceptance.ACCEPTED;
    }

    private void requireOpenWithOpening() {
        if (status == SessionStatus.CLOSED) {
            throw new IllegalStateException("The cash is already closed");
        }
        if (!hasOperationalOpening()) {
            throw new IllegalStateException("The cash has no operational opening");
        }
    }

    private static void requireToday(BusinessDate date, BusinessDate today) {
        if (!date.equals(today)) {
            throw new IllegalArgumentException("Only today can be opened: " + date.value());
        }
    }
}
