package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyFact;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyProjections;
import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.util.List;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public final class UpdateMonthlyProjection {

    public UpdateMonthlyProjection(
            ProcessedEvents processed, MonthlyProjections projections, TenantCalendar calendar, UnitOfWork unitOfWork) {}

    public record BatchResult(List<ConsumeOutcome> outcomes, List<String> failedEventIds) {}

    public ConsumeOutcome execute(String tenantId, MonthlyFact fact, String eventType) {
        throw new UnsupportedOperationException();
    }

    public BatchResult executeBatch(String tenantId, List<MonthlyFact> facts, String eventType) {
        throw new UnsupportedOperationException();
    }
}
