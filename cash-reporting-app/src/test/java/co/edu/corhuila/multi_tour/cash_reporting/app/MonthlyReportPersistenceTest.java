package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyProjection;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyTotals;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyProjections;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;
import java.sql.Date;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * I level, HU-CSH-003: the adapters of the monthly projection against a migrated PostgreSQL, as the application user. One row per
 * tenant and period, every component read back as written, the total a formula that may be negative, and the tenant leads every
 * read.
 */
@RequiresTestDatabase
class MonthlyReportPersistenceTest extends PersistenceTestBase {

    private static final ReportedPeriod OCTOBER = ReportedPeriod.of(YearMonth.of(2026, 10));
    private static final ReportedPeriod NOVEMBER = ReportedPeriod.of(YearMonth.of(2026, 11));
    private static final ReportedPeriod DECEMBER = ReportedPeriod.of(YearMonth.of(2026, 12));

    @Autowired
    MonthlyProjections projections;
    @Autowired
    MonthlyReportSource source;

    private final String tenant = UUID.randomUUID().toString();
    private final String other = UUID.randomUUID().toString();

    private static MonthlyProjection full() {
        return new MonthlyProjection(
                new MonthlyTotals(Cents.of(100), Cents.of(50), Cents.of(30), Cents.of(20), Cents.of(10), 7),
                Set.of("r1", "r2"), Map.of("c1", Cents.of(500), "c2", Cents.of(250)), Cents.of(9_000));
    }

    @Test
    void aProjectionIsStoredAndReadBackWithEveryComponent() {
        projections.save(tenant, NOVEMBER, full());

        assertThat(projections.find(tenant, NOVEMBER)).contains(full());
        Map<String, Object> row = jdbc.queryForMap(
                "select * from cash_reporting.monthly_administrative_report where tenant_id = ?::uuid", tenant);
        assertThat(row.get("starts_on")).isEqualTo(Date.valueOf("2026-11-01"));
        assertThat(row.get("ends_on")).isEqualTo(Date.valueOf("2026-11-30"));
        assertThat(((Number) row.get("period_income_cents")).longValue()).isEqualTo(150);
        assertThat(((Number) row.get("consolidated_cash_total_cents")).longValue()).isEqualTo(90);
    }

    @Test
    void theSalesStayUnknownAndTheTotalMayBeNegative() {
        MonthlyProjection unknown = new MonthlyProjection(
                new MonthlyTotals(Cents.of(100), Cents.ZERO, Cents.of(300), Cents.ZERO, Cents.ZERO, 2), Set.of(), Map.of(), null);

        projections.save(tenant, NOVEMBER, unknown);

        assertThat(projections.find(tenant, NOVEMBER)).contains(unknown);
        assertThat(source.find(tenant, NOVEMBER).orElseThrow().confirmedSales()).isNull();
        assertThat(jdbc.queryForObject("select consolidated_cash_total_cents from cash_reporting.monthly_administrative_report"
                + " where tenant_id = ?::uuid", Long.class, tenant)).isEqualTo(-200);
    }

    @Test
    void savingTheSamePeriodTwiceKeepsOneRowWithTheNewState() {
        projections.save(tenant, NOVEMBER, full());
        MonthlyProjection next = full().withCancellation("r3");

        projections.save(tenant, NOVEMBER, next);

        assertThat(projections.find(tenant, NOVEMBER)).contains(next);
        assertThat(jdbc.queryForObject("select count(*) from cash_reporting.monthly_administrative_report"
                + " where tenant_id = ?::uuid", Integer.class, tenant)).isEqualTo(1);
    }

    @Test
    void aTenantNeverReadsThePeriodOfAnother() {
        projections.save(tenant, NOVEMBER, full());

        assertThat(projections.find(other, NOVEMBER)).isEmpty();
        assertThat(source.find(other, NOVEMBER)).isEmpty();
        assertThat(source.count(other)).isZero();
        assertThat(source.newestFirst(other, 0, 10)).isEmpty();
    }

    @Test
    void thePeriodsAreListedNewestFirstInPagesAndCounted() {
        projections.save(tenant, OCTOBER, full());
        projections.save(tenant, DECEMBER, full());
        projections.save(tenant, NOVEMBER, full());
        projections.save(other, NOVEMBER, full());

        assertThat(source.count(tenant)).isEqualTo(3);
        assertThat(source.newestFirst(tenant, 0, 2)).extracting(MonthlyPeriod::period).containsExactly(DECEMBER, NOVEMBER);
        assertThat(source.newestFirst(tenant, 2, 2)).extracting(MonthlyPeriod::period).containsExactly(OCTOBER);
        assertThat(source.countNewerThan(tenant, OCTOBER)).isEqualTo(2);
        assertThat(source.countNewerThan(tenant, DECEMBER)).isZero();
        assertThat(source.find(tenant, NOVEMBER).orElseThrow().registeredCancellations()).isEqualTo(2);
        assertThat(source.find(tenant, NOVEMBER).orElseThrow().registeredOperationalCosts()).isEqualTo(Cents.of(750));
    }
}
