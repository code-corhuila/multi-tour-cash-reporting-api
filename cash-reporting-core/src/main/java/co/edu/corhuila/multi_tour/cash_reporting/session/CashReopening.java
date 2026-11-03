package co.edu.corhuila.multi_tour.cash_reporting.session;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import java.time.Instant;
import java.util.Objects;

/**
 * One reopening of a closed day: who, when, why and the closure it reopens. It is history: inserted once, never
 * rewritten. The closure it points at stays in its own table.
 */
public record CashReopening(
        String id,
        String tenantId,
        BusinessDate date,
        String closureId,
        String reason,
        String reopenedBy,
        Instant reopenedAt) {

    public CashReopening {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(closureId, "closureId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(reopenedBy, "reopenedBy");
        Objects.requireNonNull(reopenedAt, "reopenedAt");
    }
}
