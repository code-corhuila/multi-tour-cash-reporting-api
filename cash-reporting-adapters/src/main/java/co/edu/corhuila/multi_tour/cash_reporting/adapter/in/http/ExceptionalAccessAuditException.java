package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

/** The use of an exceptional access could not be audited: the read is not served (fail closed). It carries no message and no cause, so no data of the request can reach an answer or a log. */
public class ExceptionalAccessAuditException extends RuntimeException {
    public ExceptionalAccessAuditException() {
        super(null, null, false, false);
    }
}
