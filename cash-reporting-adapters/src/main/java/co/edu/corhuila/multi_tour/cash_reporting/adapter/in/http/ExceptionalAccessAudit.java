package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

/** Port: the audit of each use of an exceptional access token (HU-SEC-007 G-SEC9-1, AC7). It never receives data of the request, only who authorized it and which route was reached. If it cannot record the use it throws ExceptionalAccessAuditException and the read is not served (fail closed). */
public interface ExceptionalAccessAudit {
    void used(ExceptionalAccessUse use);
}
