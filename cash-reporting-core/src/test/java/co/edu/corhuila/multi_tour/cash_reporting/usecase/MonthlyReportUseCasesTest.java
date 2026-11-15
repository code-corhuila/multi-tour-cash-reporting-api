package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.CUSTOMER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.MANAGER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.ZonedTenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyTotals;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.PeriodSummary;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryMonthlyReportSource;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A level, HU-CSH-003 T-03 and the permission part of T-06: the three use cases over an in-memory source. Today is
 * 2026-10-14 in the zone of the tenant, so the period of the report date is 2026-10.
 */
class MonthlyReportUseCasesTest {

    private static final ReportedPeriod OCTOBER = period(2026, 10);
    private static final ReportedPeriod SEPTEMBER = period(2026, 9);
    private static final ReportedPeriod AUGUST = period(2026, 8);
    private static final ReportedPeriod NOVEMBER = period(2026, 11);

    private final CashFixture world = new CashFixture();
    private final InMemoryMonthlyReportSource source = new InMemoryMonthlyReportSource();
    private final ListMonthlyReports reports = new ListMonthlyReports(world.permissions, source, world.calendar);
    private final ListMonthlyConsolidations consolidations =
            new ListMonthlyConsolidations(world.permissions, source, world.calendar);
    private final GetMonthlyReportSummary summary = new GetMonthlyReportSummary(world.permissions, source, world.calendar);

    private static ReportedPeriod period(int year, int month) {
        return ReportedPeriod.of(YearMonth.of(year, month));
    }

    private static MonthlyPeriod withCancellations(ReportedPeriod period, int cancellations) {
        return new MonthlyPeriod(period, MonthlyTotals.EMPTY, cancellations, Cents.of(1_000L * cancellations), null);
    }

    private static List<ReportedPeriod> periods(Page<MonthlyPeriod> page) {
        return page.items().stream().map(MonthlyPeriod::period).toList();
    }

    @Test
    void aPeriodWithoutMovementsIsListedInZeroWithoutError() {
        Page<MonthlyPeriod> page = reports.execute(ADMIN, PageRequest.of(null, null));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).containsExactly(MonthlyPeriod.zero(OCTOBER));
        assertThat(page.items().get(0).totals().total()).isEqualTo(Cents.ZERO);
    }

    @Test
    void periodsAreListedNewestFirst() {
        source.put(TENANT, withCancellations(AUGUST, 1));
        source.put(TENANT, withCancellations(OCTOBER, 3));
        source.put(TENANT, withCancellations(SEPTEMBER, 2));

        Page<MonthlyPeriod> page = reports.execute(ADMIN, PageRequest.of(null, null));

        assertThat(periods(page)).containsExactly(OCTOBER, SEPTEMBER, AUGUST);
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void theZeroPeriodIsAddedAtItsPlaceAmongTheStoredOnes() {
        source.put(TENANT, withCancellations(NOVEMBER, 1));
        source.put(TENANT, withCancellations(SEPTEMBER, 2));

        Page<MonthlyPeriod> page = reports.execute(ADMIN, PageRequest.of(null, null));

        assertThat(periods(page)).containsExactly(NOVEMBER, OCTOBER, SEPTEMBER);
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void thePagesCoverEveryPeriodOnceIncludingTheZeroOne() {
        source.put(TENANT, withCancellations(NOVEMBER, 1));
        source.put(TENANT, withCancellations(SEPTEMBER, 2));
        source.put(TENANT, withCancellations(AUGUST, 3));

        assertThat(periods(reports.execute(ADMIN, new PageRequest(1, 2)))).containsExactly(NOVEMBER, OCTOBER);
        assertThat(periods(reports.execute(ADMIN, new PageRequest(2, 2)))).containsExactly(SEPTEMBER, AUGUST);
        assertThat(reports.execute(ADMIN, new PageRequest(3, 2)).items()).isEmpty();
        assertThat(reports.execute(ADMIN, new PageRequest(2, 2)).total()).isEqualTo(4);
    }

    @Test
    void aPageAfterTheZeroPeriodReadsTheStoredOnesThatFollowIt() {
        source.put(TENANT, withCancellations(SEPTEMBER, 2));
        source.put(TENANT, withCancellations(AUGUST, 3));

        assertThat(periods(reports.execute(ADMIN, new PageRequest(1, 1)))).containsExactly(OCTOBER);
        assertThat(periods(reports.execute(ADMIN, new PageRequest(2, 1)))).containsExactly(SEPTEMBER);
        assertThat(periods(reports.execute(ADMIN, new PageRequest(3, 1)))).containsExactly(AUGUST);
    }

    @Test
    void theStoredPeriodOfTheReportDateIsNotListedTwice() {
        source.put(TENANT, withCancellations(OCTOBER, 5));

        Page<MonthlyPeriod> page = reports.execute(ADMIN, PageRequest.of(null, null));

        assertThat(page.items()).containsExactly(withCancellations(OCTOBER, 5));
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void reportAndConsolidationListTheSamePeriodsWithTheSameValues() {
        source.put(TENANT, withCancellations(SEPTEMBER, 2));
        source.put(TENANT, withCancellations(AUGUST, 3));

        Page<MonthlyPeriod> report = reports.execute(ADMIN, PageRequest.of(null, null));
        Page<MonthlyPeriod> consolidation = consolidations.execute(ADMIN, PageRequest.of(null, null));

        assertThat(consolidation).isEqualTo(report);
    }

    @Test
    void summaryOfNoPeriodAskedIsTheLatestPeriodOfTheReport() {
        source.put(TENANT, withCancellations(SEPTEMBER, 2));

        PeriodSummary latest = summary.execute(ADMIN, null);

        assertThat(latest.period()).isEqualTo(OCTOBER);
        assertThat(latest.cancellations()).isZero();
        assertThat(latest.periodIncome()).isEqualTo(Cents.ZERO);
    }

    @Test
    void summaryOfAPeriodReadsItsFourValuesAndTheUnknownSalesStayNull() {
        source.put(TENANT, withCancellations(SEPTEMBER, 2));

        PeriodSummary asked = summary.execute(ADMIN, SEPTEMBER);

        assertThat(asked.cancellations()).isEqualTo(2);
        assertThat(asked.operationalCosts()).isEqualTo(Cents.of(2_000));
        assertThat(asked.confirmedSales()).isNull();
    }

    @Test
    void summaryCarriesTheSalesWhenThereIsAValueAndZerosForAPeriodWithNoRow() {
        source.put(TENANT, new MonthlyPeriod(AUGUST, MonthlyTotals.EMPTY, 0, Cents.ZERO, Cents.of(90_000)));

        assertThat(summary.execute(ADMIN, AUGUST).confirmedSales()).isEqualTo(Cents.of(90_000));
        assertThat(summary.execute(ADMIN, period(2025, 1)))
                .isEqualTo(PeriodSummary.of(MonthlyPeriod.zero(period(2025, 1))));
    }

    @Test
    void theZeroPeriodUsesTheZoneInForce() {
        // 02:00 UTC on 1 November is still 31 October in Bogota and already 1 November in Tokyo.
        Instant now = Instant.parse("2026-11-01T02:00:00Z");
        TenantCalendar bogota = new ZonedTenantCalendar(() -> now, tenantId -> ZoneId.of("America/Bogota"));
        TenantCalendar tokyo = new ZonedTenantCalendar(() -> now, tenantId -> ZoneId.of("Asia/Tokyo"));

        Page<MonthlyPeriod> inBogota =
                new ListMonthlyReports(world.permissions, source, bogota).execute(ADMIN, PageRequest.of(null, null));
        Page<MonthlyPeriod> inTokyo =
                new ListMonthlyReports(world.permissions, source, tokyo).execute(ADMIN, PageRequest.of(null, null));

        assertThat(periods(inBogota)).containsExactly(OCTOBER);
        assertThat(periods(inTokyo)).containsExactly(NOVEMBER);
    }

    @Test
    void twoTenantsWithTheSamePeriodNeverSeeEachOther() {
        Actor other = new Actor("tenant-2", "admin-2", Set.of("TENANT_ADMINISTRATOR"), Set.of());
        source.put(TENANT, withCancellations(SEPTEMBER, 2));
        source.put("tenant-2", withCancellations(SEPTEMBER, 7));

        assertThat(summary.execute(ADMIN, SEPTEMBER).cancellations()).isEqualTo(2);
        assertThat(summary.execute(other, SEPTEMBER).cancellations()).isEqualTo(7);
        assertThat(reports.execute(other, PageRequest.of(null, null)).items())
                .contains(withCancellations(SEPTEMBER, 7))
                .doesNotContain(withCancellations(SEPTEMBER, 2));
    }

    @Test
    void theRolesThatReadAreAdministratorCollaboratorManagerAccountantAndAnalyst() {
        for (Actor reader : List.of(
                ADMIN,
                COLLABORATOR,
                MANAGER,
                new Actor(TENANT, "u-4", Set.of("ACCOUNTANT"), Set.of()),
                new Actor(TENANT, "u-5", Set.of("ANALYST"), Set.of()))) {
            assertThat(reports.execute(reader, PageRequest.of(null, null)).items()).isNotEmpty();
            assertThat(consolidations.execute(reader, PageRequest.of(null, null)).items()).isNotEmpty();
            assertThat(summary.execute(reader, null)).isNotNull();
        }
    }

    @Test
    void finalCustomerPlatformAdministratorUnknownRoleAndNoActorAreForbidden() {
        for (Actor refused : new Actor[] {
            CUSTOMER,
            new Actor(null, "p-1", Set.of("PLATFORM_ADMINISTRATOR"), Set.of()),
            new Actor(TENANT, "u-1", Set.of("SUPER_USER"), Set.of()),
            new Actor(TENANT, "u-2", Set.of(), Set.of()),
            null
        }) {
            assertThatThrownBy(() -> reports.execute(refused, PageRequest.of(null, null)))
                    .isInstanceOf(CashRuleViolation.Forbidden.class);
            assertThatThrownBy(() -> consolidations.execute(refused, PageRequest.of(null, null)))
                    .isInstanceOf(CashRuleViolation.Forbidden.class);
            assertThatThrownBy(() -> summary.execute(refused, null)).isInstanceOf(CashRuleViolation.Forbidden.class);
        }
    }
}
