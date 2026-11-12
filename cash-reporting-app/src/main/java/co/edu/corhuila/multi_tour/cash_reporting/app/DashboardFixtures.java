package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ConsumeDepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ConsumeReservationEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Demo data of the daily dashboard (HU-CSH-002 AC-DB.12, plan D-11, DP-API-7). Off unless {@code cash.fixtures.enabled=true}; it
 * is on in the compose of the demo. It replays the events of the day of the contract example (created 12, pending 6, confirmed 18,
 * with balance 3, cancelled 2, upcoming 4) through the same consumers as the real events, for the tenant of
 * {@code cash.fixtures.tenant-id}, relative to the tenant's today. The ids of its events hold the day, so a restart on the same
 * day changes nothing. No personal datum: ids, states and instants only.
 */
@Component("dashboardFixtures")
@ConditionalOnProperty(name = "cash.fixtures.enabled", havingValue = "true")
class DashboardFixtures implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardFixtures.class);
    private static final Duration DAY = Duration.ofDays(1);

    private final ConsumeReservationEvent reservations;
    private final ConsumeDepartureEvent departures;
    private final TenantCalendar calendar;
    private final String tenantId;

    DashboardFixtures(ConsumeReservationEvent reservations, ConsumeDepartureEvent departures, TenantCalendar calendar,
            @Value("${cash.fixtures.tenant-id:}") String tenantId) {
        this.reservations = reservations;
        this.departures = departures;
        this.calendar = calendar;
        this.tenantId = tenantId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantId == null || tenantId.isBlank()) {
            LOG.warn("The fixtures of the dashboard are on but cash.fixtures.tenant-id is empty: nothing is loaded");
            return;
        }
        BusinessDate today = calendar.today(tenantId);
        Instant start = today.value().atStartOfDay(calendar.zoneOf(tenantId)).toInstant();
        for (int i = 1; i <= 12; i++) {
            Instant at = start.plus(Duration.ofMinutes(i));
            created(today, "new-" + i, at);
            if (i > 6) {
                confirmed(today, "new-" + i, at.plus(Duration.ofHours(1)), i <= 9 ? "DEPOSIT_RECEIVED" : "FULLY_PAID");
            }
            Instant before = start.minus(DAY).plus(Duration.ofMinutes(i));
            created(today, "old-" + i, before);
            confirmed(today, "old-" + i, before.plus(Duration.ofHours(1)), "FULLY_PAID");
        }
        for (int i = 1; i <= 2; i++) {
            Instant before = start.minus(DAY).plus(Duration.ofMinutes(i));
            created(today, "cancelled-" + i, before);
            Instant at = start.plus(Duration.ofMinutes(30 + i));
            reservations.execute(tenantId, ReservationEvent.state(eventId(today, "cancelled-" + i, "x"), id(today, "cancelled-" + i),
                    ReservationStatus.CANCELLED, null, at), "ReservationCancelled");
        }
        for (int i = 1; i <= 4; i++) {
            Instant at = start.plus(Duration.ofHours(7 + i));
            departures.execute(tenantId, DepartureEvent.scheduled(eventId(today, "departure-" + i, "s"),
                    id(today, "departure-" + i), at, start.minus(DAY)), "SalidaScheduled");
        }
        LOG.info("The fixtures of the dashboard were loaded for the tenant of the configuration");
    }

    private void created(BusinessDate day, String label, Instant at) {
        reservations.execute(tenantId, ReservationEvent.created(eventId(day, label, "c"), id(day, label), at),
                "ReservationCreated");
    }

    private void confirmed(BusinessDate day, String label, Instant at, String payment) {
        reservations.execute(tenantId, ReservationEvent.state(eventId(day, label, "k"), id(day, label),
                ReservationStatus.CONFIRMED, payment, at), "ReservationConfirmed");
    }

    private String id(BusinessDate day, String label) {
        return uuid("reservation:" + day.value() + ":" + label);
    }

    private String eventId(BusinessDate day, String label, String kind) {
        return uuid("event:" + day.value() + ":" + label + ":" + kind);
    }

    private String uuid(String seed) {
        return UUID.nameUUIDFromBytes((tenantId + ":" + seed).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
