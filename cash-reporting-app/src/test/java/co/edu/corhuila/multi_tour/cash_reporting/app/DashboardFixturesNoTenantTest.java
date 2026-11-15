package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryDashboardFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** The switch on without a tenant loads nothing: there is no tenant to load it for. */
@SpringBootTest(properties = "cash.fixtures.enabled=true")
@Import(InMemoryWorld.class)
class DashboardFixturesNoTenantTest {

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
    void theLoaderStartsAndSaysNothingToLoad() {
        assertThat(context.containsBean("dashboardFixtures")).isTrue();
    }
}
