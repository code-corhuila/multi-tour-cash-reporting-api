package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryDashboardFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** The fixtures are off by default: a fresh start loads no demo data (AC-DB.12, DP-API-7). */
@SpringBootTest
@Import(InMemoryWorld.class)
class DashboardFixturesOffTest {

    @Autowired
    InMemoryDashboardFacts facts;
    @Autowired
    ApplicationContext context;

    @Test
    void nothingIsLoaded() {
        assertThat(facts.reservations(DashboardFixturesTest.TENANT)).isEmpty();
        assertThat(facts.departures(DashboardFixturesTest.TENANT)).isEmpty();
    }

    @Test
    void theLoaderIsNotEvenCreated() {
        assertThat(context.containsBean("dashboardFixtures")).isFalse();
    }
}
