package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyFact;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyProjection;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.UpdateMonthlyProjection.BatchResult;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryMonthlyProjections;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryProcessedEvents;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A level, HU-CSH-003 T-04 and the application part of T-02 and T-13 (AC-03.4, AC-03.10, AC-03.17, AC-03.18): the updater
 * applies each fact once to its month over in-memory fakes. The facts of other domains are PROPUESTO (fixture): their real
 * events are not built, so each test says which fact it uses.
 */
class UpdateMonthlyProjectionTest {

    private static final String OTHER_TENANT = "tenant-2";
    private static final String TYPE = "MonthlyFact";
    private static final ReportedPeriod SEPTEMBER = ReportedPeriod.of(YearMonth.of(2026, 9));
    private static final ReportedPeriod OCTOBER = ReportedPeriod.of(YearMonth.of(2026, 10));
    private static final Instant IN_SEPTEMBER = Instant.parse("2026-09-15T15:00:00Z");
    private static final Instant IN_OCTOBER = Instant.parse("2026-10-05T15:00:00Z");

    private final CashFixture world = new CashFixture();
    private final InMemoryProcessedEvents processed = new InMemoryProcessedEvents();
    private final InMemoryMonthlyProjections projections = new InMemoryMonthlyProjections();
    private final UpdateMonthlyProjection updater =
            new UpdateMonthlyProjection(processed, projections, world.calendar, world.unitOfWork);

    private ConsumeOutcome apply(String tenant, MonthlyFact fact) {
        return updater.execute(tenant, fact, TYPE);
    }

    private MonthlyPeriod month(String tenant, ReportedPeriod period) {
        return projections.find(tenant, period).orElse(MonthlyProjection.EMPTY).toPeriod(period);
    }

    private static RecordedMovement income(String id, LocalDate date, PaymentMedium medium, long cents) {
        return new RecordedMovement(
                id,
                BusinessDate.of(date),
                new CashMovement(MovementType.INCOME, medium, Cents.amount(cents)),
                "concept",
                false,
                IN_SEPTEMBER,
                "user-1",
                null);
    }

    private static MonthlyFact cancelled(String eventId, String reservation, Instant at) {
        return new MonthlyFact.ReservationCancelled(eventId, reservation, at);
    }

    private static MonthlyFact cost(String eventId, String costId, long cents, Instant at) {
        return new MonthlyFact.OperationalCostRecorded(eventId, costId, Cents.of(cents), at);
    }

    private static MonthlyFact confirmed(String eventId, String reservation, long cents, Instant at) {
        return new MonthlyFact.ReservationConfirmed(eventId, reservation, Cents.of(cents), at);
    }

    @Test
    void aMovementUpdatesTheMonthOfTheBusinessDateOfItsCash() {
        MonthlyFact fact = new MonthlyFact.MovementRecorded(
                "e1", income("m1", LocalDate.of(2026, 9, 30), PaymentMedium.TRANSFER, 40_000));

        assertThat(apply(TENANT, fact)).isEqualTo(ConsumeOutcome.APPLIED);

        MonthlyPeriod september = month(TENANT, SEPTEMBER);
        assertThat(september.totals().transferIncome()).isEqualTo(Cents.of(40_000));
        assertThat(september.totals().movementCount()).isEqualTo(1);
        assertThat(projections.find(TENANT, OCTOBER)).isEmpty();
    }

    @Test
    void theSameEventDeliveredTwiceChangesNothing() {
        MonthlyFact fact = cancelled("e1", "r1", IN_SEPTEMBER);

        assertThat(apply(TENANT, fact)).isEqualTo(ConsumeOutcome.APPLIED);
        assertThat(apply(TENANT, fact)).isEqualTo(ConsumeOutcome.DUPLICATE);

        assertThat(month(TENANT, SEPTEMBER).registeredCancellations()).isEqualTo(1);
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void anEventOfAnotherTenantNeverTouchesThisTenant() {
        apply(TENANT, cancelled("e1", "r1", IN_SEPTEMBER));
        // The same event id and reservation id under another tenant are a different event of a different month.
        assertThat(apply(OTHER_TENANT, cancelled("e1", "r1", IN_SEPTEMBER))).isEqualTo(ConsumeOutcome.APPLIED);
        apply(OTHER_TENANT, cancelled("e2", "r2", IN_SEPTEMBER));

        assertThat(month(TENANT, SEPTEMBER).registeredCancellations()).isEqualTo(1);
        assertThat(month(OTHER_TENANT, SEPTEMBER).registeredCancellations()).isEqualTo(2);
    }

    @Test
    void aFailingEventDoesNotStopTheBatch() {
        Set<String> full = new HashSet<>();
        for (int i = 0; i < MonthlyProjection.MAX_ELEMENTS; i++) {
            full.add("r-" + i);
        }
        projections.save(TENANT, SEPTEMBER, new MonthlyProjection(
                co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyTotals.EMPTY, full, Map.of(), null));

        BatchResult result = updater.executeBatch(
                TENANT,
                List.of(
                        cost("e1", "c1", 5_000, IN_SEPTEMBER),
                        cancelled("e2", "one-too-many", IN_SEPTEMBER),
                        cost("e3", "c2", 7_000, IN_SEPTEMBER)),
                TYPE);

        assertThat(result.failedEventIds()).containsExactly("e2");
        assertThat(result.outcomes()).containsExactly(ConsumeOutcome.APPLIED, ConsumeOutcome.APPLIED);
        assertThat(month(TENANT, SEPTEMBER).registeredOperationalCosts()).isEqualTo(Cents.of(12_000));
    }

    @Test
    void anEventOverTheMaximumIsNotAppliedNorTruncated() {
        Map<String, Cents> full = new HashMap<>();
        for (int i = 0; i < MonthlyProjection.MAX_ELEMENTS; i++) {
            full.put("c-" + i, Cents.of(1));
        }
        MonthlyProjection stored = new MonthlyProjection(
                co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyTotals.EMPTY, Set.of(), full, null);
        projections.save(TENANT, SEPTEMBER, stored);

        assertThatThrownBy(() -> apply(TENANT, cost("e1", "one-too-many", 9, IN_SEPTEMBER)))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);

        assertThat(projections.find(TENANT, SEPTEMBER)).contains(stored);
        assertThat(month(TENANT, SEPTEMBER).registeredOperationalCosts()).isEqualTo(Cents.of(MonthlyProjection.MAX_ELEMENTS));
        // The transaction is undone, so the event id is not kept and job 7 can retry it.
        assertThat(world.unitOfWork.rollbacks()).isEqualTo(1);
    }

    @Test
    void aCancellationRepeatedUnderAnotherEventIdCountsOnce() {
        apply(TENANT, cancelled("e1", "r1", IN_SEPTEMBER));
        apply(TENANT, cancelled("e2", "r1", IN_SEPTEMBER));

        assertThat(month(TENANT, SEPTEMBER).registeredCancellations()).isEqualTo(1);
    }

    @Test
    void aReservationIsCancelledOnceWhateverTheNumberOfEvents() {
        for (int delivery = 1; delivery <= 3; delivery++) {
            apply(TENANT, cancelled("e" + delivery, "r1", IN_SEPTEMBER));
        }
        apply(TENANT, cancelled("e4", "r2", IN_SEPTEMBER));

        assertThat(month(TENANT, SEPTEMBER).registeredCancellations()).isEqualTo(2);
    }

    @Test
    void aCostRepeatedUnderAnotherEventIdCountsOnce() {
        apply(TENANT, cost("e1", "c1", 5_000, IN_SEPTEMBER));
        apply(TENANT, cost("e2", "c1", 5_000, IN_SEPTEMBER));

        assertThat(month(TENANT, SEPTEMBER).registeredOperationalCosts()).isEqualTo(Cents.of(5_000));
    }

    @Test
    void aCostCountsOnlyWhenTheServiceWasExecuted() {
        // Only an OperationalCostRecorded fact (the cost of a service actually executed) adds a cost.
        apply(TENANT, cancelled("e1", "r1", IN_SEPTEMBER));
        apply(TENANT, confirmed("e2", "r2", 80_000, IN_SEPTEMBER));
        assertThat(month(TENANT, SEPTEMBER).registeredOperationalCosts()).isEqualTo(Cents.ZERO);

        apply(TENANT, cost("e3", "c1", 5_000, IN_SEPTEMBER));
        assertThat(month(TENANT, SEPTEMBER).registeredOperationalCosts()).isEqualTo(Cents.of(5_000));
    }

    @Test
    void confirmedSalesOfAPeriodAreTheSumOfItsDailySales() {
        apply(TENANT, confirmed("e1", "r1", 80_000, Instant.parse("2026-09-10T15:00:00Z")));
        apply(TENANT, confirmed("e2", "r2", 20_000, Instant.parse("2026-09-11T15:00:00Z")));
        apply(TENANT, confirmed("e3", "r3", 5_000, IN_OCTOBER));

        assertThat(month(TENANT, SEPTEMBER).confirmedSales()).isEqualTo(Cents.of(100_000));
        assertThat(month(TENANT, OCTOBER).confirmedSales()).isEqualTo(Cents.of(5_000));
    }

    @Test
    void aMonthWithNoConfirmationKeepsItsSalesUnknown() {
        apply(TENANT, cost("e1", "c1", 5_000, IN_SEPTEMBER));

        assertThat(month(TENANT, SEPTEMBER).confirmedSales()).isNull();
    }

    @Test
    void aCancellationDoesNotRemoveASaleOfAnotherMonth() {
        apply(TENANT, confirmed("e1", "r1", 80_000, IN_SEPTEMBER));
        apply(TENANT, cancelled("e2", "r1", IN_OCTOBER));

        assertThat(month(TENANT, SEPTEMBER).confirmedSales()).isEqualTo(Cents.of(80_000));
        assertThat(month(TENANT, SEPTEMBER).registeredCancellations()).isZero();
        assertThat(month(TENANT, OCTOBER).registeredCancellations()).isEqualTo(1);
    }

    @Test
    void anEventAfterItsMonthIsAppliedToItsMonth() {
        // Today is 2026-10-14 in the zone of the tenant: a cost of September that arrives now still goes to September.
        apply(TENANT, cost("e1", "c1", 5_000, IN_SEPTEMBER));

        assertThat(month(TENANT, SEPTEMBER).registeredOperationalCosts()).isEqualTo(Cents.of(5_000));
        assertThat(projections.find(TENANT, OCTOBER)).isEmpty();
    }

    @Test
    void aFactOfAnInstantIsReadInTheZoneOfTheTenant() {
        // 03:00 UTC on 1 October is 22:00 on 30 September in Bogota.
        apply(TENANT, cancelled("e1", "r1", Instant.parse("2026-10-01T03:00:00Z")));

        assertThat(month(TENANT, SEPTEMBER).registeredCancellations()).isEqualTo(1);
        assertThat(month(TENANT, OCTOBER).registeredCancellations()).isZero();
    }

    @Test
    void aBlankIdOrANegativeAmountIsRefusedWhenTheFactIsBuilt() {
        assertThatThrownBy(() -> cancelled(" ", "r1", IN_SEPTEMBER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cancelled("e1", null, IN_SEPTEMBER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cost("e1", "c1", -1, IN_SEPTEMBER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> confirmed("e1", "r1", -1, IN_SEPTEMBER)).isInstanceOf(IllegalArgumentException.class);
    }
}
