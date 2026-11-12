package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR_WITHOUT_PERMISSION;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.CUSTOMER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.MANAGER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DashboardCounts;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DashboardIndicators;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryDashboardFacts;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryProcessedEvents;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A level, AC-DB.1, AC-DB.4, AC-DB.5, AC-DB.6, AC-DB.7, AC-DB.9 and AC-DB.10 over in-memory fakes: the consumer applies an
 * event once and acknowledges a repeat without effect, an older event changes nothing, and the read computes the six sets of
 * the tenant's local day from the facts (so a day without events still shows the stock of the earlier ones).
 */
class DashboardUseCasesTest {

    private static final Instant MORNING = Instant.parse("2026-10-14T15:00:00Z");
    private static final Instant YESTERDAY = Instant.parse("2026-10-13T15:00:00Z");

    private final CashFixture world = new CashFixture();
    private final InMemoryDashboardFacts facts = new InMemoryDashboardFacts();
    private final InMemoryProcessedEvents processed = new InMemoryProcessedEvents();
    private final ConsumeReservationEvent consumeReservation =
            new ConsumeReservationEvent(processed, facts, world.unitOfWork);
    private final ConsumeDepartureEvent consumeDeparture = new ConsumeDepartureEvent(processed, facts, world.unitOfWork);
    private final GetDailyDashboard dashboard = new GetDailyDashboard(world.permissions, facts, world.calendar);

    private ConsumeOutcome reservation(String tenant, ReservationEvent event) {
        return consumeReservation.execute(tenant, event, "ReservationEvent");
    }

    @Test
    void aDayWithoutFactsIsSixZeros() {
        DashboardIndicators day = dashboard.execute(ADMIN);

        assertThat(day.counts()).isEqualTo(new DashboardCounts(0, 0, 0, 0, 0, 0));
        assertThat(day.reportDate()).isEqualTo(TODAY);
    }

    @Test
    void anEventIsAppliedOnceAndARepeatIsAcknowledgedWithoutEffect() {
        ReservationEvent created = ReservationEvent.created("e1", "r1", MORNING);

        assertThat(reservation(TENANT, created)).isEqualTo(ConsumeOutcome.APPLIED);
        assertThat(reservation(TENANT, created)).isEqualTo(ConsumeOutcome.DUPLICATE);

        assertThat(processed.count()).isEqualTo(1);
        assertThat(dashboard.execute(ADMIN).created()).containsExactly("r1");
    }

    @Test
    void anOlderStateEventIsRecordedButChangesNothing() {
        reservation(TENANT, ReservationEvent.created("e1", "r1", YESTERDAY));
        reservation(TENANT, ReservationEvent.state("e3", "r1", ReservationStatus.CONFIRMED, "PAID", MORNING));

        ConsumeOutcome outcome = reservation(TENANT,
                ReservationEvent.state("e2", "r1", ReservationStatus.PENDING_PAYMENT, null, YESTERDAY.plusSeconds(60)));

        assertThat(outcome).isEqualTo(ConsumeOutcome.APPLIED);
        DashboardIndicators day = dashboard.execute(ADMIN);
        assertThat(day.confirmed()).containsExactly("r1");
        assertThat(day.pendingPayment()).isEmpty();
    }

    @Test
    void aDayWithoutEventsStillShowsTheStockOfTheEarlierOnes() {
        reservation(TENANT, ReservationEvent.created("e1", "r1", YESTERDAY));
        reservation(TENANT, ReservationEvent.state("e2", "r2", ReservationStatus.CONFIRMED, "BALANCE_PENDING", YESTERDAY));

        DashboardIndicators day = dashboard.execute(ADMIN);

        assertThat(day.pendingPayment()).containsExactly("r1");
        assertThat(day.confirmed()).containsExactly("r2");
        assertThat(day.confirmedWithBalance()).containsExactly("r2");
        assertThat(day.created()).isEmpty();
    }

    @Test
    void todayIsTheLocalDayOfTheTenantNotTheServerDay() {
        // 21:00 in Bogota on the 14th is 02:00 UTC of the 15th
        reservation(TENANT, ReservationEvent.created("e1", "r1", Instant.parse("2026-10-15T02:00:00Z")));

        assertThat(dashboard.execute(ADMIN).created()).containsExactly("r1");
    }

    @Test
    void aDepartureThatStartsTodayIsUpcomingUntilItStarts() {
        consumeDeparture.execute(TENANT, DepartureEvent.scheduled("d-e1", "d1", MORNING.plusSeconds(7_200), YESTERDAY), "SalidaScheduled");
        assertThat(dashboard.execute(ADMIN).upcomingServices()).containsExactly("d1");

        consumeDeparture.execute(TENANT,
                DepartureEvent.state("d-e2", "d1", co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureStatus.EXECUTION_STARTED, MORNING),
                "SalidaExecutionStarted");

        assertThat(dashboard.execute(ADMIN).upcomingServices()).isEmpty();
    }

    @Test
    void theFactsOfAnotherTenantNeverAppear() {
        reservation("tenant-2", ReservationEvent.created("e1", "r1", MORNING));

        assertThat(dashboard.execute(ADMIN).counts()).isEqualTo(new DashboardCounts(0, 0, 0, 0, 0, 0));
    }

    @Test
    void theSameEventIdOfTwoTenantsIsTwoEvents() {
        assertThat(reservation(TENANT, ReservationEvent.created("same", "r1", MORNING))).isEqualTo(ConsumeOutcome.APPLIED);
        assertThat(reservation("tenant-2", ReservationEvent.created("same", "r1", MORNING))).isEqualTo(ConsumeOutcome.APPLIED);
    }

    @Test
    void theTenantRolesReadAndEveryOtherRoleIsRefused() {
        Actor accountant = new Actor(TENANT, "acc-1", Set.of("ACCOUNTANT"), Set.of());
        Actor analyst = new Actor(TENANT, "ana-1", Set.of("ANALYST"), Set.of());
        for (Actor reader : new Actor[] {ADMIN, COLLABORATOR_WITHOUT_PERMISSION, MANAGER, accountant, analyst}) {
            assertThat(dashboard.execute(reader).reportDate()).as(reader.userId()).isEqualTo(TODAY);
        }
        for (Actor refused : new Actor[] {CUSTOMER, WORKER}) {
            assertThatThrownBy(() -> dashboard.execute(refused)).as(refused.userId()).isInstanceOf(CashRuleViolation.Forbidden.class);
        }
    }
}
