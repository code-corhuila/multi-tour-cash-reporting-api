package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;

/** Checks shared by the use cases; they turn the domain facts into the family of rule violations. */
final class CashGuards {

    private CashGuards() {}

    static void requireAllowed(CashPermissions permissions, Actor actor, Operation operation) {
        if (!permissions.allows(actor, operation)) {
            throw new CashRuleViolation.Forbidden("The user may not do " + operation);
        }
    }

    /** A base is an integer of cents, zero or more. */
    static Cents requireBase(Long cents) {
        if (cents == null || cents < 0) {
            throw new IllegalArgumentException("The base must be an integer of cents >= 0");
        }
        return Cents.of(cents);
    }

    static void requireNotPending(CashSession session, BusinessDate today) {
        if (session.isPending(today)) {
            throw new CashRuleViolation.PendingClosure("The cash of " + session.date().value() + " is pending closure");
        }
    }

    static void requireNotClosed(CashSession session) {
        if (session.status() == SessionStatus.CLOSED) {
            throw new CashRuleViolation.AlreadyClosed("The cash of " + session.date().value() + " is closed");
        }
    }
}
