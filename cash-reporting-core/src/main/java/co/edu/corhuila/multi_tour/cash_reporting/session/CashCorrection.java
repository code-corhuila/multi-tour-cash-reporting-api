package co.edu.corhuila.multi_tour.cash_reporting.session;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import java.time.Instant;
import java.util.Objects;

/**
 * A note that justifies an exceptional correction of a closed day: who, when and why. It never carries a figure, so it
 * cannot change the closure it refers to.
 */
public record CashCorrection(
        String id,
        String tenantId,
        BusinessDate date,
        String closureId,
        String justification,
        String registeredBy,
        Instant registeredAt) {

    public CashCorrection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(closureId, "closureId");
        Objects.requireNonNull(justification, "justification");
        Objects.requireNonNull(registeredBy, "registeredBy");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}
