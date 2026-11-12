package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureFact;
import co.edu.corhuila.multi_tour.cash_reporting.port.DashboardFacts;
import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.util.Objects;

/** Consumes an event of a departure, as {@link ConsumeReservationEvent} does for a reservation. */
public final class ConsumeDepartureEvent {

    private final ProcessedEvents processed;
    private final DashboardFacts facts;
    private final UnitOfWork unitOfWork;

    public ConsumeDepartureEvent(ProcessedEvents processed, DashboardFacts facts, UnitOfWork unitOfWork) {
        this.processed = Objects.requireNonNull(processed, "processed");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public ConsumeOutcome execute(String tenantId, DepartureEvent event, String eventType) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(event, "event");
        return unitOfWork.inTransaction(() -> {
            if (!processed.firstTime(tenantId, event.eventId(), eventType)) {
                return ConsumeOutcome.DUPLICATE;
            }
            DepartureFact fact = facts.findDeparture(tenantId, event.departureId())
                    .orElseGet(() -> DepartureFact.empty(event.departureId()));
            facts.saveDeparture(tenantId, fact.apply(event));
            return ConsumeOutcome.APPLIED;
        });
    }
}
