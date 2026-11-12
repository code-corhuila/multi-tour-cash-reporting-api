package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D level, RN-DB2, RN-DB3, RN-DB9, RN-DB10 and RN-DB11 of the dashboard (DP-API-1 by default): pending, confirmed and
 * confirmed with balance are the stock of the reservations; created and cancelled are the flow of the local day of the
 * tenant (DP-API-2: the zone is the configured one, shared with the cash); the upcoming services are the departures that
 * start today (DP-DB4).
 */
class DashboardIndicatorsTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final BusinessDate TODAY = BusinessDate.of(LocalDate.of(2026, 11, 3));
    // 10:00 local on the day of the report
    private static final Instant MORNING = Instant.parse("2026-11-03T15:00:00Z");
    private static final Instant YESTERDAY = Instant.parse("2026-11-02T15:00:00Z");

    private static ReservationFact reservation(String id, ReservationStatus status, String situation, Instant createdAt) {
        return ReservationFact.empty(id)
                .apply(ReservationEvent.created("c-" + id, id, createdAt))
                .apply(ReservationEvent.state("s-" + id, id, status, situation, createdAt.plusSeconds(60)));
    }

    private static DashboardIndicators of(List<ReservationFact> reservations, List<DepartureFact> departures) {
        return DashboardIndicators.of(reservations, departures, TODAY, BOGOTA);
    }

    @Test
    void aDayWithoutFactsIsSixZeros() {
        DashboardIndicators day = of(List.of(), List.of());

        assertThat(day.counts()).isEqualTo(new DashboardCounts(0, 0, 0, 0, 0, 0));
        assertThat(day.reportDate()).isEqualTo(TODAY);
    }

    @Test
    void pendingPaymentCountsOnlyReservationsThatHaveNotMetTheCondition() {
        DashboardIndicators day = of(List.of(
                reservation("r1", ReservationStatus.PENDING_PAYMENT, null, MORNING),
                reservation("r2", ReservationStatus.CONFIRMED, "PAID", MORNING),
                reservation("r3", ReservationStatus.CONFIRMED, "BALANCE_PENDING", MORNING)), List.of());

        assertThat(day.pendingPayment()).containsExactly("r1");
    }

    @Test
    void pendingPaymentIsTheStockNotTheFlowOfTheDay() {
        DashboardIndicators day = of(List.of(reservation("r1", ReservationStatus.PENDING_PAYMENT, null, YESTERDAY)), List.of());

        assertThat(day.pendingPayment()).containsExactly("r1");
        assertThat(day.created()).isEmpty();
    }

    @Test
    void confirmedWithBalanceIsDepositReceivedOrBalancePending() {
        DashboardIndicators day = of(List.of(
                reservation("r1", ReservationStatus.CONFIRMED, "DEPOSIT_RECEIVED", MORNING),
                reservation("r2", ReservationStatus.CONFIRMED, "BALANCE_PENDING", MORNING),
                reservation("r3", ReservationStatus.CONFIRMED, "PAID", MORNING)), List.of());

        assertThat(day.confirmedWithBalance()).containsExactlyInAnyOrder("r1", "r2");
    }

    @Test
    void aPaidInFullConfirmedReservationIsNotWithBalance() {
        DashboardIndicators day = of(List.of(reservation("r1", ReservationStatus.CONFIRMED, "PAID", MORNING)), List.of());

        assertThat(day.confirmed()).containsExactly("r1");
        assertThat(day.confirmedWithBalance()).isEmpty();
    }

    @Test
    void confirmedIncludesConfirmedWithBalance() {
        DashboardIndicators day = of(List.of(
                reservation("r1", ReservationStatus.CONFIRMED, "BALANCE_PENDING", MORNING),
                reservation("r2", ReservationStatus.CONFIRMED, "PAID", MORNING)), List.of());

        assertThat(day.confirmed()).containsExactlyInAnyOrder("r1", "r2");
        assertThat(day.confirmed()).containsAll(day.confirmedWithBalance());
    }

    @Test
    void cancelledCountsTheCancellationsRegisteredToday() {
        ReservationFact today = reservation("r1", ReservationStatus.PENDING_PAYMENT, null, YESTERDAY)
                .apply(ReservationEvent.state("x1", "r1", ReservationStatus.CANCELLED, null, MORNING));
        ReservationFact earlier = reservation("r2", ReservationStatus.PENDING_PAYMENT, null, YESTERDAY.minusSeconds(86_400))
                .apply(ReservationEvent.state("x2", "r2", ReservationStatus.CANCELLED, null, YESTERDAY));

        DashboardIndicators day = of(List.of(today, earlier), List.of());

        assertThat(day.cancelled()).containsExactly("r1");
    }

    @Test
    void aCancelledReservationLeavesPendingAndConfirmed() {
        ReservationFact cancelled = reservation("r1", ReservationStatus.CONFIRMED, "BALANCE_PENDING", YESTERDAY)
                .apply(ReservationEvent.state("x1", "r1", ReservationStatus.CANCELLED, null, MORNING));

        DashboardIndicators day = of(List.of(cancelled), List.of());

        assertThat(day.pendingPayment()).isEmpty();
        assertThat(day.confirmed()).isEmpty();
        assertThat(day.confirmedWithBalance()).isEmpty();
        assertThat(day.cancelled()).containsExactly("r1");
    }

    @Test
    void createdCountsTheReservationsCreatedOnTheLocalDay() {
        DashboardIndicators day = of(List.of(
                reservation("r1", ReservationStatus.PENDING_PAYMENT, null, MORNING),
                reservation("r2", ReservationStatus.PENDING_PAYMENT, null, YESTERDAY)), List.of());

        assertThat(day.created()).containsExactly("r1");
    }

    @Test
    void aReservationCreatedAtTwentyThreeThirtyLocalBelongsToThatLocalDay() {
        // 23:30 in Bogota on the 3rd is 04:30 UTC of the 4th: the server's day would be the next one
        Instant lateNight = Instant.parse("2026-11-04T04:30:00Z");

        DashboardIndicators day = of(List.of(reservation("r1", ReservationStatus.PENDING_PAYMENT, null, lateNight)), List.of());

        assertThat(day.created()).containsExactly("r1");
    }

    @Test
    void theTenantDayFollowsTheTenantTimeZone() {
        List<ReservationFact> facts = List.of(
                reservation("r1", ReservationStatus.PENDING_PAYMENT, null, Instant.parse("2026-11-04T04:30:00Z")));

        assertThat(DashboardIndicators.of(facts, List.of(), TODAY, BOGOTA).created()).containsExactly("r1");
        assertThat(DashboardIndicators.of(facts, List.of(), TODAY, ZoneId.of("UTC")).created()).isEmpty();
    }

    @Test
    void upcomingServicesCountsScheduledDeparturesStartingToday() {
        DashboardIndicators day = of(List.of(), List.of(
                DepartureFact.empty("d1").apply(DepartureEvent.scheduled("e1", "d1", MORNING.plusSeconds(7_200), MORNING)),
                DepartureFact.empty("d2").apply(DepartureEvent.scheduled("e2", "d2", MORNING.plusSeconds(86_400), MORNING))));

        assertThat(day.upcomingServices()).containsExactly("d1");
    }

    @Test
    void aCancelledOrStartedDepartureIsNotUpcoming() {
        Instant start = MORNING.plusSeconds(7_200);
        DashboardIndicators day = of(List.of(), List.of(
                DepartureFact.empty("d1").apply(DepartureEvent.scheduled("e1", "d1", start, MORNING))
                        .apply(DepartureEvent.state("e2", "d1", DepartureStatus.CANCELLED, MORNING.plusSeconds(10))),
                DepartureFact.empty("d2").apply(DepartureEvent.scheduled("e3", "d2", start, MORNING))
                        .apply(DepartureEvent.state("e4", "d2", DepartureStatus.EXECUTION_STARTED, MORNING.plusSeconds(10)))));

        assertThat(day.upcomingServices()).isEmpty();
    }

    @Test
    void theCountsAreTheLengthsOfTheSets() {
        DashboardIndicators day = of(List.of(
                reservation("r1", ReservationStatus.CONFIRMED, "BALANCE_PENDING", MORNING),
                reservation("r2", ReservationStatus.CONFIRMED, "PAID", MORNING),
                reservation("r3", ReservationStatus.PENDING_PAYMENT, null, MORNING)), List.of());

        assertThat(day.counts()).isEqualTo(new DashboardCounts(3, 1, 2, 1, 0, 0));
    }
}
