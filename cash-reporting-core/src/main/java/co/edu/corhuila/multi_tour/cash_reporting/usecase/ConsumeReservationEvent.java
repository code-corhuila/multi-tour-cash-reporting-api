package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationFact;
import co.edu.corhuila.multi_tour.cash_reporting.port.DashboardFacts;
import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.util.Objects;

/**
 * Consumes an event of a reservation (AC-DB.9, AC-DB.10): in one transaction it records the event id and applies the event
 * to the last known state of the reservation. A repeat of an event is acknowledged without effect; an older state event is
 * recorded and ignored by the fact itself. The tenant of the event is the one the receiving route gives, never the payload's.
 */
public final class ConsumeReservationEvent {

    private final ProcessedEvents processed;
    private final DashboardFacts facts;
    private final UnitOfWork unitOfWork;

    public ConsumeReservationEvent(ProcessedEvents processed, DashboardFacts facts, UnitOfWork unitOfWork) {
        this.processed = Objects.requireNonNull(processed, "processed");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public ConsumeOutcome execute(String tenantId, ReservationEvent event, String eventType) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(event, "event");
        return unitOfWork.inTransaction(() -> {
            if (!processed.firstTime(tenantId, event.eventId(), eventType)) {
                return ConsumeOutcome.DUPLICATE;
            }
            ReservationFact fact = facts.findReservation(tenantId, event.reservationId())
                    .orElseGet(() -> ReservationFact.empty(event.reservationId()));
            facts.saveReservation(tenantId, fact.apply(event));
            return ConsumeOutcome.APPLIED;
        });
    }
}
