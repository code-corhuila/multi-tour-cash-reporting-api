package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryDashboardFacts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * C level, getDailyDashboard of HU-CSH-002 (AC-DB.1, AC-DB.4 to AC-DB.8): every response is validated against
 * openapi/openapi.yaml. The facts are stored as the consumers leave them; the fixed clock says it is 2026-11-03 07:00 in
 * Bogota. Each test works with a tenant of its own, and two tenants prove the isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class DashboardControllersTest {

    private static final String PATH = "/api/v1/cash-reporting/daily-dashboard";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant TODAY_NOON = Instant.parse("2026-11-03T17:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-11-01T17:00:00Z");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private InMemoryDashboardFacts facts;

    private final String tenant = UUID.randomUUID().toString();

    private static String token(String tenantOfToken, String role) {
        Map<String, Object> claims = TestKeys.claims(role);
        claims.put("tenantId", tenantOfToken);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MockHttpServletRequestBuilder read(String tenantOfToken, String role) {
        return get(PATH).header("Authorization", "Bearer " + token(tenantOfToken, role));
    }

    private MockHttpServletRequestBuilder read(String role) {
        return read(tenant, role);
    }

    private JsonNode answered(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private void reservation(String tenantOfFact, ReservationEvent event) {
        ReservationFact current = facts.findReservation(tenantOfFact, event.reservationId())
                .orElseGet(() -> ReservationFact.empty(event.reservationId()));
        facts.saveReservation(tenantOfFact, current.apply(event));
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    @Test
    void aDayWithoutReservationsAnswers200WithSixZeros() throws Exception {
        JsonNode day = answered(read("TENANT_ADMINISTRATOR"), 200);

        assertThat(day.path("reportDate").asText()).isEqualTo("2026-11-03");
        for (String field : new String[] {"createdReservations", "pendingPaymentReservations", "confirmedReservations",
                "confirmedWithBalance", "cancelledReservations", "upcomingServices"}) {
            assertThat(day.path(field).asInt(-1)).as(field).isZero();
        }
    }

    @Test
    void theSixNumbersAreTheOnesOfTheTenantLocalDay() throws Exception {
        reservation(tenant, ReservationEvent.created(id(), "r1", TODAY_NOON));
        reservation(tenant, ReservationEvent.created(id(), "r2", EARLIER));
        reservation(tenant, ReservationEvent.state(id(), "r2", ReservationStatus.CONFIRMED, "BALANCE_PENDING", EARLIER.plusSeconds(60)));
        reservation(tenant, ReservationEvent.state(id(), "r3", ReservationStatus.CONFIRMED, "PAID", EARLIER));
        reservation(tenant, ReservationEvent.state(id(), "r4", ReservationStatus.CANCELLED, null, TODAY_NOON));
        DepartureFact departure = DepartureFact.empty("d1")
                .apply(DepartureEvent.scheduled(id(), "d1", TODAY_NOON.plusSeconds(3_600), EARLIER));
        facts.saveDeparture(tenant, departure);

        JsonNode day = answered(read("MANAGER"), 200);

        assertThat(day.path("createdReservations").asInt()).isEqualTo(1);
        assertThat(day.path("pendingPaymentReservations").asInt()).isEqualTo(1);
        assertThat(day.path("confirmedReservations").asInt()).isEqualTo(2);
        assertThat(day.path("confirmedWithBalance").asInt()).isEqualTo(1);
        assertThat(day.path("cancelledReservations").asInt()).isEqualTo(1);
        assertThat(day.path("upcomingServices").asInt()).isEqualTo(1);
    }

    @Test
    void theFiveTenantRolesReadIt() throws Exception {
        for (String role : new String[] {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST"}) {
            answered(read(role), 200);
        }
    }

    @Test
    void aFinalCustomerAndAPlatformAdministratorAnswer403() throws Exception {
        for (String role : new String[] {"FINAL_CUSTOMER", "PLATFORM_ADMINISTRATOR", "SOMETHING_ELSE"}) {
            JsonNode refused = answered(read(role), 403);
            assertThat(refused.path("error").asText()).as(role).isEqualTo("FORBIDDEN");
        }
    }

    @Test
    void aRequestWithoutTokenOrWithAnInvalidTokenAnswers401() throws Exception {
        answered(get(PATH), 401);
        answered(get(PATH).header("Authorization", "Bearer not-a-token"), 401);
    }

    @Test
    void twoTenantsNeverSeeTheFactsOfEachOther() throws Exception {
        String other = UUID.randomUUID().toString();
        reservation(other, ReservationEvent.created(id(), "r1", TODAY_NOON));
        reservation(other, ReservationEvent.created(id(), "r2", TODAY_NOON));
        reservation(tenant, ReservationEvent.created(id(), "r9", TODAY_NOON));

        assertThat(answered(read(tenant, "TENANT_ADMINISTRATOR"), 200).path("createdReservations").asInt()).isEqualTo(1);
        assertThat(answered(read(other, "TENANT_ADMINISTRATOR"), 200).path("createdReservations").asInt()).isEqualTo(2);
    }

    @Test
    void aTenantSentByTheCallerIsNeverUsed() throws Exception {
        String other = UUID.randomUUID().toString();
        reservation(other, ReservationEvent.created(id(), "r1", TODAY_NOON));

        JsonNode day = answered(read("TENANT_ADMINISTRATOR").param("tenantId", other).header("X-Tenant-Id", other), 200);

        assertThat(day.path("createdReservations").asInt()).isZero();
    }
}
