package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureStatus;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.port.DashboardFacts;
import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * I level, HU-CSH-002: the adapters of the facts and of the processed events against a migrated PostgreSQL, as the
 * application user. The last known state is one row per tenant and id, a repeated event id is recognised, and the
 * tenant leads every read.
 */
@RequiresTestDatabase
class DashboardFactsPersistenceTest extends PersistenceTestBase {

    private static final Instant T1 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    DashboardFacts facts;
    @Autowired
    ProcessedEvents processed;

    private final String tenant = UUID.randomUUID().toString();
    private final String other = UUID.randomUUID().toString();

    private String id() {
        return UUID.randomUUID().toString();
    }

    @Test
    void aReservationFactIsStoredAndReadBackWithEveryField() {
        ReservationFact fact = ReservationFact.empty("r1")
                .apply(ReservationEvent.created("e1", "r1", T1))
                .apply(ReservationEvent.state("e2", "r1", ReservationStatus.CONFIRMED, "BALANCE_PENDING", T2));

        facts.saveReservation(tenant, fact);

        assertThat(facts.findReservation(tenant, "r1")).contains(fact);
    }

    @Test
    void savingTheSameReservationTwiceKeepsOneRowWithTheNewState() {
        ReservationFact first = ReservationFact.empty("r1").apply(ReservationEvent.created("e1", "r1", T1));
        facts.saveReservation(tenant, first);
        ReservationFact second = first.apply(ReservationEvent.state("e2", "r1", ReservationStatus.CANCELLED, null, T2));

        facts.saveReservation(tenant, second);

        assertThat(facts.reservations(tenant)).containsExactly(second);
        assertThat(facts.findReservation(tenant, "r1").orElseThrow().status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void theSameReservationIdOfTwoTenantsIsTwoFacts() {
        facts.saveReservation(tenant, ReservationFact.empty("r1").apply(ReservationEvent.created("e1", "r1", T1)));
        facts.saveReservation(other, ReservationFact.empty("r1").apply(ReservationEvent.state("e2", "r1", ReservationStatus.CONFIRMED, "PAID", T2)));

        assertThat(facts.findReservation(tenant, "r1").orElseThrow().status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(facts.findReservation(other, "r1").orElseThrow().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(facts.reservations(tenant)).hasSize(1);
    }

    @Test
    void aDepartureFactKeepsItsStartAcrossAStateChange() {
        DepartureFact fact = DepartureFact.empty("d1")
                .apply(DepartureEvent.scheduled("e1", "d1", T2, T1))
                .apply(DepartureEvent.state("e2", "d1", DepartureStatus.EXECUTION_STARTED, T2));

        facts.saveDeparture(tenant, fact);

        assertThat(facts.findDeparture(tenant, "d1")).contains(fact);
        assertThat(facts.findDeparture(tenant, "d1").orElseThrow().scheduledStart()).isEqualTo(T2);
        assertThat(facts.departures(other)).isEmpty();
    }

    @Test
    void aProcessedEventIsNewTheFirstTimeAndARepeatTheSecond() {
        String eventId = id();

        assertThat(processed.firstTime(tenant, eventId, "ReservationConfirmed")).isTrue();
        assertThat(processed.firstTime(tenant, eventId, "ReservationConfirmed")).isFalse();
    }

    @Test
    void theSameEventIdOfAnotherTenantIsNew() {
        String eventId = id();
        processed.firstTime(tenant, eventId, "ReservationConfirmed");

        assertThat(processed.firstTime(other, eventId, "ReservationConfirmed")).isTrue();
    }
}
