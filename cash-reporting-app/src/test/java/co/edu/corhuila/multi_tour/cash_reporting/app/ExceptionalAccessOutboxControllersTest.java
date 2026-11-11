package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryOutbox;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11): through the real filter and the real audit adapter, a read let through by the token of an exceptional access leaves one outbox event
 * PlatformExceptionalAccessUsed with the closed payload; a refused call leaves none. No database: the outbox is the in-memory fake.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class ExceptionalAccessOutboxControllersTest {

    private static final String BASE = "/api/v1/cash-reporting";
    private static final UUID AUTHORIZATION = UUID.randomUUID();
    private static final String TENANT = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private InMemoryOutbox outbox;

    @BeforeEach
    void clean() {
        outbox.events().clear();
    }

    private static String token(String tenant, List<String> scope) {
        Map<String, Object> claims = TestKeys.claims("PLATFORM_ADMINISTRATOR");
        if (tenant == null) claims.remove("tenantId"); else claims.put("tenantId", tenant);
        claims.put("access", "exceptional");
        claims.put("scope", scope);
        claims.put("authorizationId", AUTHORIZATION.toString());
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private int answer(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", "corr-9").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }

    @Test
    void aReadInsideTheScopeLeavesOneOutboxEventWithTheClosedPayload() throws Exception {
        assertThat(answer(get(BASE + "/cash-days/2026-11-03/movements"), token(TENANT, List.of("CASH")))).isNotIn(401, 403);

        assertThat(outbox.events()).hasSize(1);
        InMemoryOutbox.Event event = outbox.events().get(0);
        assertThat(event.eventType()).isEqualTo("PlatformExceptionalAccessUsed");
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.aggregateId()).isEqualTo(AUTHORIZATION.toString());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("authorizationId", AUTHORIZATION.toString());
        expected.put("platformAdministratorId", TestKeys.ACCOUNT);
        expected.put("tenantId", TENANT);
        expected.put("method", "GET");
        expected.put("route", BASE + "/cash-days/2026-11-03/movements");
        expected.put("area", "CASH");
        expected.put("occurredAt", TestKeys.NOW.toString());
        expected.put("correlationId", "corr-9");
        assertThat(event.payload()).isEqualTo(expected);
    }

    @Test
    void aRefusedCallLeavesNoEvent() throws Exception {
        assertThat(answer(get(BASE + "/cash-days"), token(TENANT, List.of("RESERVATIONS")))).isEqualTo(403);
        assertThat(answer(post(BASE + "/cash-days/2026-11-03/open"), token(TENANT, List.of("CASH")))).isEqualTo(403);
        assertThat(answer(get(BASE + "/internal/cash-days/open"), token(TENANT, List.of("CASH")))).isEqualTo(403);

        assertThat(outbox.events()).isEmpty();
    }

    @Test
    void aTokenWithoutTenantIsUnauthorizedAndLeavesNoEvent() throws Exception {
        assertThat(answer(get(BASE + "/cash-days"), token(null, List.of("CASH")))).isEqualTo(401);

        assertThat(outbox.events()).isEmpty();
    }
}
