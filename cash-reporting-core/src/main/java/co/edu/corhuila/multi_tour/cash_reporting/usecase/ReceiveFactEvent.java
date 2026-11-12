package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyFact;
import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The door of the events of other domains (receiveFactEvent, ADR-004 D4 and D5): only a service may deliver, and the tenant is
 * the one of the route, never the payload's. It hands the event to the consumer of its kind. An event that feeds the dashboard
 * and the month (a reservation confirmed with its value, a reservation cancelled) is recorded once, with ONE key, and applied
 * to both in the same transaction: the key is unique per tenant and event, so two consumers could not each record it.
 */
public final class ReceiveFactEvent {

    private final ProcessedEvents processed;
    private final UnitOfWork unitOfWork;
    private final ConsumeReservationEvent reservations;
    private final ConsumeDepartureEvent departures;
    private final UpdateMonthlyProjection months;
    private final ApplyTenantLocalization localizations;

    public ReceiveFactEvent(ProcessedEvents processed, UnitOfWork unitOfWork, ConsumeReservationEvent reservations,
            ConsumeDepartureEvent departures, UpdateMonthlyProjection months, ApplyTenantLocalization localizations) {
        this.processed = Objects.requireNonNull(processed, "processed");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.departures = Objects.requireNonNull(departures, "departures");
        this.months = Objects.requireNonNull(months, "months");
        this.localizations = Objects.requireNonNull(localizations, "localizations");
    }

    /** The permission is answered before the shape of the request is looked at (plan D-06 step 2). */
    public void authorize(Actor service) {
        CashGuards.requireService(service);
    }

    /** @param month the fact the same event brings for the monthly report, or null when it brings none */
    public ConsumeOutcome reservation(
            Actor service, String tenantId, String eventType, ReservationEvent event, MonthlyFact month) {
        authorize(service);
        if (month == null) {
            return reservations.execute(tenantId, event, eventType);
        }
        return unitOfWork.inTransaction(() -> {
            if (!processed.firstTime(tenantId, event.eventId(), eventType)) {
                return ConsumeOutcome.DUPLICATE;
            }
            reservations.apply(tenantId, event);
            months.apply(tenantId, month);
            return ConsumeOutcome.APPLIED;
        });
    }

    public ConsumeOutcome departure(Actor service, String tenantId, String eventType, DepartureEvent event) {
        authorize(service);
        return departures.execute(tenantId, event, eventType);
    }

    public ConsumeOutcome month(Actor service, String tenantId, String eventType, MonthlyFact fact) {
        authorize(service);
        return months.execute(tenantId, fact, eventType);
    }

    public ConsumeOutcome localization(
            Actor service, String tenantId, String eventType, String eventId, ZoneId zone, Instant occurredAt) {
        authorize(service);
        return localizations.execute(tenantId, eventId, eventType, zone, occurredAt);
    }
}
