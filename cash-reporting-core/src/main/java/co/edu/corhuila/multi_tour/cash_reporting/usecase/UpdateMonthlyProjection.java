package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyFact;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyProjection;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.PeriodOf;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyProjections;
import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies one fact to the month it belongs to (AC-03.10, AC-03.17, AC-03.18): in one transaction it records the event id and
 * changes the month, so a repeat of the event is acknowledged without effect (RN-03). An event that would take a set over its
 * maximum is refused whole and the transaction is undone, so the event can be retried. The tenant is the one the receiving
 * route gives, never the payload's, and every row touched belongs to it. An event that arrives after its month goes to its
 * month: a month is never frozen (DP-RM-A4).
 */
public final class UpdateMonthlyProjection {

    private final ProcessedEvents processed;
    private final MonthlyProjections projections;
    private final TenantCalendar calendar;
    private final UnitOfWork unitOfWork;

    public UpdateMonthlyProjection(
            ProcessedEvents processed, MonthlyProjections projections, TenantCalendar calendar, UnitOfWork unitOfWork) {
        this.processed = Objects.requireNonNull(processed, "processed");
        this.projections = Objects.requireNonNull(projections, "projections");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    /** What a batch did: one outcome per fact that was not refused, and the ids of the events that failed. */
    public record BatchResult(List<ConsumeOutcome> outcomes, List<String> failedEventIds) {}

    public ConsumeOutcome execute(String tenantId, MonthlyFact fact, String eventType) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(fact, "fact");
        return unitOfWork.inTransaction(() -> {
            if (!processed.firstTime(tenantId, fact.eventId(), eventType)) {
                return ConsumeOutcome.DUPLICATE;
            }
            apply(tenantId, fact);
            return ConsumeOutcome.APPLIED;
        });
    }

    /** Changes the month of the fact; the caller has already recorded the event id in its transaction. */
    public void apply(String tenantId, MonthlyFact fact) {
        ReportedPeriod period = periodOf(tenantId, fact);
        MonthlyProjection current = projections.find(tenantId, period).orElse(MonthlyProjection.EMPTY);
        projections.save(tenantId, period, apply(current, fact));
    }

    /** A failing fact does not stop the others: it is listed by its event id only, for the caller to retry. */
    public BatchResult executeBatch(String tenantId, List<MonthlyFact> facts, String eventType) {
        List<ConsumeOutcome> outcomes = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (MonthlyFact fact : facts) {
            try {
                outcomes.add(execute(tenantId, fact, eventType));
            } catch (RuntimeException failure) {
                failed.add(fact.eventId());
            }
        }
        return new BatchResult(List.copyOf(outcomes), List.copyOf(failed));
    }

    private ReportedPeriod periodOf(String tenantId, MonthlyFact fact) {
        ZoneId zone = calendar.zoneOf(tenantId);
        return switch (fact) {
            case MonthlyFact.MovementRecorded recorded -> PeriodOf.movement(recorded.movement());
            case MonthlyFact.ReservationCancelled cancelled -> PeriodOf.moment(cancelled.occurredAt(), zone);
            case MonthlyFact.OperationalCostRecorded cost -> PeriodOf.moment(cost.occurredAt(), zone);
            case MonthlyFact.ReservationConfirmed confirmed -> PeriodOf.moment(confirmed.occurredAt(), zone);
        };
    }

    private static MonthlyProjection apply(MonthlyProjection current, MonthlyFact fact) {
        return switch (fact) {
            case MonthlyFact.MovementRecorded recorded -> current.withMovement(recorded.movement());
            case MonthlyFact.ReservationCancelled cancelled -> current.withCancellation(cancelled.reservationId());
            case MonthlyFact.OperationalCostRecorded cost -> current.withCost(cost.costId(), cost.amount());
            case MonthlyFact.ReservationConfirmed confirmed -> current.withSale(confirmed.commercialValue());
        };
    }
}
