package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import java.util.Objects;

/**
 * The door of the events of other domains (receiveFactEvent, ADR-004 D4 and D5): only a service may deliver, and the tenant is
 * the one of the route, never the payload's. It hands the event to the consumer of its kind; the consumer applies it once.
 */
public final class ReceiveFactEvent {

    private final ConsumeReservationEvent reservations;
    private final ConsumeDepartureEvent departures;

    public ReceiveFactEvent(ConsumeReservationEvent reservations, ConsumeDepartureEvent departures) {
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.departures = Objects.requireNonNull(departures, "departures");
    }

    /** The permission is answered before the shape of the request is looked at (plan D-06 step 2). */
    public void authorize(Actor service) {
        CashGuards.requireService(service);
    }

    public ConsumeOutcome reservation(Actor service, String tenantId, String eventType, ReservationEvent event) {
        authorize(service);
        return reservations.execute(tenantId, event, eventType);
    }

    public ConsumeOutcome departure(Actor service, String tenantId, String eventType, DepartureEvent event) {
        authorize(service);
        return departures.execute(tenantId, event, eventType);
    }
}
