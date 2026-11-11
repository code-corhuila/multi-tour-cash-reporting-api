package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryPendingClosureAlert;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * C level, the internal routes of job 8 (HU-CSH-001): only a SERVICE_TOKEN, which has no tenant of its own, is accepted;
 * the tenant is the one of the path. Every response is validated against openapi/openapi.yaml.
 */
// The sweep reads every tenant of the shared in-memory store: a fresh context keeps the sessions that other classes seeded
// (with tenant ids that are not UUIDs) out of this list.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class InternalRoutesControllersTest extends WriteControllersSupport {

    @Autowired
    InMemoryPendingClosureAlert alerts;

    private String serviceToken() {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", "multi-tour-worker-cash-close");
        claims.remove("tenantId");
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private String autoClose(String date) {
        return BASE + "/internal/tenants/" + tenant + "/cash-days/" + date + "/auto-close";
    }

    private void automaticDay(co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate date) {
        sessions.seed(CashSession.automatic(tenant, date, BOGOTA));
    }

    @Test
    void theSweepListsTheOpenDaysAcrossTenantsWithWhatTheWorkerNeeds() throws Exception {
        automaticDay(YESTERDAY);
        seedOpen(TODAY, 100_000);

        JsonNode page = answered(get(BASE + "/internal/cash-days/open").header("Authorization", "Bearer " + serviceToken())
                .queryParam("limit", "100"), 200);

        assertThat(page.path("meta").path("total").asLong()).isGreaterThanOrEqualTo(2);
        List<JsonNode> mine = new java.util.ArrayList<>();
        page.path("data").forEach(item -> {
            if (item.path("tenantId").asText().equals(tenant)) {
                mine.add(item);
            }
        });
        assertThat(mine).hasSize(2);
        JsonNode automatic = mine.get(0);
        assertThat(automatic.path("businessDate").asText()).isEqualTo("2026-11-02");
        assertThat(automatic.path("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(automatic.path("origin").asText()).isEqualTo("AUTOMATIC_CREATION");
        assertThat(automatic.path("hasOperationalOpening").asBoolean()).isFalse();
        assertThat(mine.get(1).path("hasOperationalOpening").asBoolean()).isTrue();
    }

    @Test
    void aPersonCannotReadTheSweepAndAServiceTokenIsRefusedOnThePersonRoutes() throws Exception {
        answered(get(BASE + "/internal/cash-days/open").header("Authorization", "Bearer " + token("TENANT_ADMINISTRATOR")), 403);
        answered(get(BASE + "/cash-days/current").header("Authorization", "Bearer " + serviceToken()), 403);
        answered(get(BASE + "/internal/cash-days/open"), 401);
    }

    @Test
    void theAutoCloseClosesAnAutomaticCashOfTheTenantOfThePath() throws Exception {
        automaticDay(YESTERDAY);

        JsonNode day = answered(post(autoClose("2026-11-02")).header("Authorization", "Bearer " + serviceToken()), 200);

        assertThat(day.path("status").asText()).isEqualTo("CLOSED");
        assertThat(day.path("closingMode").asText()).isEqualTo("AUTOMATIC");
        CashSession session = sessions.find(tenant, YESTERDAY).orElseThrow();
        assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(session.closingMode()).isEqualTo(ClosingMode.AUTOMATIC);
        assertThat(outbox.events().stream().filter(event -> event.tenantId().equals(tenant))
                .map(event -> event.eventType()).toList()).containsExactly("CashSessionAutoClosed");
    }

    @Test
    void aRepeatedAutoCloseAnswersTheSameDayAgain() throws Exception {
        automaticDay(YESTERDAY);
        String header = "Bearer " + serviceToken();
        answered(post(autoClose("2026-11-02")).header("Authorization", header), 200);

        JsonNode again = answered(post(autoClose("2026-11-02")).header("Authorization", header), 200);

        assertThat(again.path("closingMode").asText()).isEqualTo("AUTOMATIC");
        assertThat(eventsOfTheTenant()).isEqualTo(1);
    }

    @Test
    void aCashWithOperationalOpeningStaysPendingAndRaisesTheAlert() throws Exception {
        seedOpen(YESTERDAY, 100_000);

        JsonNode refused = answered(post(autoClose("2026-11-02")).header("Authorization", "Bearer " + serviceToken()), 422);

        assertThat(errorOf(refused)).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(sessions.find(tenant, YESTERDAY).orElseThrow().status()).isEqualTo(SessionStatus.OPEN);
        assertThat(alerts.raised()).anyMatch(raised -> raised.tenantId().equals(tenant));
        assertThat(eventsOfTheTenant()).isZero();
    }

    @Test
    void aDayThatHasNotEndedIsRefusedAndADateWithoutCashIsNotFound() throws Exception {
        automaticDay(TODAY);
        String header = "Bearer " + serviceToken();

        assertThat(errorOf(answered(post(autoClose("2026-11-03")).header("Authorization", header), 422)))
                .isEqualTo("BUSINESS_RULE_VIOLATION");
        answered(post(autoClose("2026-10-01")).header("Authorization", header), 404);
    }

    @Test
    void aPersonCannotCloseAutomaticallyAndTheTenantOfThePathIsAUuid() throws Exception {
        automaticDay(YESTERDAY);

        answered(post(autoClose("2026-11-02")).header("Authorization", "Bearer " + token("TENANT_ADMINISTRATOR")), 403);
        answered(post(BASE + "/internal/tenants/not-a-uuid/cash-days/2026-11-02/auto-close")
                .header("Authorization", "Bearer " + serviceToken()), 400);
        assertThat(sessions.find(tenant, YESTERDAY).orElseThrow().status()).isEqualTo(SessionStatus.OPEN);
    }
}
