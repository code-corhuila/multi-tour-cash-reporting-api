package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DashboardCounts;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DashboardIndicators;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryDashboardFacts;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * The demo data of the dashboard (HU-CSH-002 AC-DB.12, plan D-11, DP-API-7) with the switch on: the fixtures go through the
 * same consumers as the real events and the day they build gives the six numbers of the contract example.
 */
@SpringBootTest(properties = {"cash.fixtures.enabled=true", "cash.fixtures.tenant-id=" + DashboardFixturesTest.TENANT})
@Import(InMemoryWorld.class)
class DashboardFixturesTest {

    static final String TENANT = "00000000-0000-0000-0000-0000000000f1";
    static final BusinessDate TODAY = BusinessDate.of(LocalDate.of(2026, 11, 3));
    static final DashboardCounts SIX_ZEROS = new DashboardCounts(0, 0, 0, 0, 0, 0);

    static DashboardCounts countsOf(InMemoryDashboardFacts facts, String tenant) {
        return DashboardIndicators.of(
                facts.reservations(tenant), facts.departures(tenant), TODAY, ZoneId.of("America/Bogota")).counts();
    }

    @Autowired
    InMemoryDashboardFacts facts;
    @Autowired
    ApplicationContext context;

    @Test
    void theFixtureDayProducesTheSixNumbersOfTheContractExample() {
        assertThat(countsOf(facts, TENANT)).isEqualTo(new DashboardCounts(12, 6, 18, 3, 2, 4));
    }

    @Test
    void aSecondLoadChangesNothingAndAnotherTenantHasNoFixtures() throws Exception {
        int reservations = facts.reservations(TENANT).size();

        context.getBean("dashboardFixtures", ApplicationRunner.class).run(null);

        assertThat(facts.reservations(TENANT)).hasSize(reservations);
        assertThat(countsOf(facts, TENANT)).isEqualTo(new DashboardCounts(12, 6, 18, 3, 2, 4));
        assertThat(countsOf(facts, UUID.randomUUID().toString())).isEqualTo(SIX_ZEROS);
    }
}
