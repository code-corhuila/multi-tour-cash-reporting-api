package co.edu.corhuila.multi_tour.cash_reporting.error;

/**
 * The errors of the cash rules. The core throws one of these; the HTTP adapter alone decides status and code. The
 * messages are in English: the Spanish text of the mockup belongs to the response, never to the core.
 */
public abstract sealed class CashRuleViolation extends RuntimeException
        permits CashRuleViolation.AlreadyOpen,
                CashRuleViolation.AlreadyClosed,
                CashRuleViolation.NoOpenCash,
                CashRuleViolation.PendingClosure,
                CashRuleViolation.ReasonRequired,
                CashRuleViolation.InvalidTransition,
                CashRuleViolation.RuleViolation,
                CashRuleViolation.NotFound,
                CashRuleViolation.PaymentAlreadyRecorded,
                CashRuleViolation.Forbidden {

    private CashRuleViolation(String message) {
        super(message);
    }

    /** The day already has its operational opening. */
    public static final class AlreadyOpen extends CashRuleViolation {
        public AlreadyOpen(String message) {
            super(message);
        }
    }

    /** The date is closed. */
    public static final class AlreadyClosed extends CashRuleViolation {
        public AlreadyClosed(String message) {
            super(message);
        }
    }

    /** Cash needs an operational opening and the day has none. */
    public static final class NoOpenCash extends CashRuleViolation {
        public NoOpenCash(String message) {
            super(message);
        }
    }

    /** The day is pending closure: only its formal closing is accepted. */
    public static final class PendingClosure extends CashRuleViolation {
        public PendingClosure(String message) {
            super(message);
        }
    }

    /** A reopening reason or a correction justification is missing or blank. */
    public static final class ReasonRequired extends CashRuleViolation {
        public ReasonRequired(String message) {
            super(message);
        }
    }

    /** Another state rule, for example a correction on a day that is not closed. */
    public static final class InvalidTransition extends CashRuleViolation {
        public InvalidTransition(String message) {
            super(message);
        }
    }

    /** Any other business rule, for example a date that is not today where only today is allowed. */
    public static final class RuleViolation extends CashRuleViolation {
        public RuleViolation(String message) {
            super(message);
        }
    }

    /** The payment already has its income in the cash: a payment is recorded once, whatever the idempotency key. */
    public static final class PaymentAlreadyRecorded extends CashRuleViolation {
        public PaymentAlreadyRecorded(String message) {
            super(message);
        }
    }

    /** The record the operation needs does not exist for this tenant. */
    public static final class NotFound extends CashRuleViolation {
        public NotFound(String message) {
            super(message);
        }
    }

    /** The actor lacks the role or permission for the operation. */
    public static final class Forbidden extends CashRuleViolation {
        public Forbidden(String message) {
            super(message);
        }
    }
}
