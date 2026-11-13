package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemorySessionCutoffs;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * C level, HU-SEC-005 AC4 and HU-SEC-006 AC7 (RN-CSH-API-40 to 47): the receiving route accepts only a SERVICE token, keeps the
 * cutoff once and never moves it back; the token filter answers 401 to a token of the account issued before the cutoff.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class SessionRevocationRoutesControllersTest {

    private static final String ROUTE = "/api/v1/cash-reporting/internal/events";
    private static final String PROTECTED = "/api/v1/cash-reporting/cash-days/current";
    private static final Instant CUTOFF = TestKeys.NOW.minusSeconds(600);

    @Autowired
    MockMvc mvc;
    @Autowired
    InMemorySessionCutoffs cutoffs;

    private final String account = UUID.randomUUID().toString();

    private String serviceToken() {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", "multi-tour-worker-event-relay");
        claims.remove("tenantId");
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private String accountToken(String role, Instant issuedAt) {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims(role));
        claims.put("sub", account);
        if (issuedAt != null) claims.put("iat", issuedAt.getEpochSecond());
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private static String event(String eventId, String accountId, Instant from, String tenantInEnvelope, String tenantInPayload) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"AccountSessionsRevoked\",\"eventVersion\":1,"
                + "\"aggregateId\":\"" + accountId + "\",\"occurredAt\":\"" + from + "\","
                + (tenantInEnvelope == null ? "" : "\"tenantId\":\"" + tenantInEnvelope + "\",")
                + "\"payload\":{\"accountId\":\"" + accountId + "\",\"revokedFrom\":\"" + from + "\""
                + (tenantInPayload == null ? "" : ",\"tenantId\":\"" + tenantInPayload + "\"") + "}}";
    }

    private MockHttpServletRequestBuilder deliver(String token, String key, String body) {
        MockHttpServletRequestBuilder request = post(ROUTE);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int deliveredAs(String eventId, Instant from, String tenantInEnvelope, String tenantInPayload) throws Exception {
        return mvc.perform(deliver(serviceToken(), eventId, event(eventId, account, from, tenantInEnvelope, tenantInPayload)))
                .andReturn().getResponse().getStatus();
    }

    private int protectedStatus(String token) throws Exception {
        return mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    @Test
    void theReceivingRouteAnswers401WithoutTokenAnd403WithoutTheServiceRole() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = event(id, account, CUTOFF, null, null);

        mvc.perform(deliver(null, id, body)).andExpect(status().isUnauthorized());
        mvc.perform(deliver(accountToken("TENANT_ADMINISTRATOR", TestKeys.NOW), id, body)).andExpect(status().isForbidden());
        assertThat(cutoffs.find(account)).isEmpty();
    }

    @Test
    void theFirstDeliveryIs201AndARepeatIs200() throws Exception {
        String id = UUID.randomUUID().toString();

        assertThat(deliveredAs(id, CUTOFF, null, null)).isEqualTo(201);
        assertThat(deliveredAs(id, CUTOFF, null, null)).isEqualTo(200);
        assertThat(cutoffs.find(account).orElseThrow().revokedFrom()).isEqualTo(CUTOFF);
    }

    @Test
    void anOlderEventLeavesTheNewerCutoffAndLaterOneMovesItForward() throws Exception {
        assertThat(deliveredAs(UUID.randomUUID().toString(), CUTOFF, null, null)).isEqualTo(201);

        assertThat(deliveredAs(UUID.randomUUID().toString(), CUTOFF.minusSeconds(60), null, null)).isEqualTo(200);
        assertThat(cutoffs.find(account).orElseThrow().revokedFrom()).isEqualTo(CUTOFF);
        assertThat(deliveredAs(UUID.randomUUID().toString(), CUTOFF.plusSeconds(60), null, null)).isEqualTo(201);
    }

    @Test
    void anEventWithAndWithoutTenantIsAccepted() throws Exception {
        String tenant = UUID.randomUUID().toString();

        assertThat(deliveredAs(UUID.randomUUID().toString(), CUTOFF, tenant, null)).isEqualTo(201);
        assertThat(cutoffs.find(account).orElseThrow().tenantId()).isEqualTo(tenant);
        assertThat(deliveredAs(UUID.randomUUID().toString(), CUTOFF.plusSeconds(1), null, tenant)).isEqualTo(201);
        assertThat(deliveredAs(UUID.randomUUID().toString(), CUTOFF.plusSeconds(2), null, null)).isEqualTo(201);
    }

    @Test
    void theBodyIsValidatedAsTheContractSays() throws Exception {
        String id = UUID.randomUUID().toString();
        String good = event(id, account, CUTOFF, null, null);

        mvc.perform(deliver(serviceToken(), id, good.replace("AccountSessionsRevoked", "TenantInactivated")))
                .andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), id, good.replace("\"accountId\":\"" + account + "\",", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), id, good.replace(CUTOFF.toString() + "\"}}", "yesterday\"}}")))
                .andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), id, good.replace("\"aggregateId\":\"" + account, "\"aggregateId\":\"" + UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), id, event(id, account, CUTOFF, "not-a-uuid", null))).andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), "another-key-0001", good)).andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), null, good)).andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), id, "{\"eventId\":\"" + id + "\"" + " ".repeat(17_000) + "}")).andExpect(status().isBadRequest());
        mvc.perform(deliver(serviceToken(), id, null)).andExpect(status().isBadRequest());
        assertThat(cutoffs.find(account)).isEmpty();
    }

    @Test
    void aTokenIssuedBeforeTheCutoffIs401AndALaterOneOrNoCutoffPasses() throws Exception {
        assertThat(protectedStatus(accountToken("TENANT_ADMINISTRATOR", CUTOFF.minusSeconds(3600)))).isEqualTo(200);
        assertThat(deliveredAs(UUID.randomUUID().toString(), CUTOFF, null, null)).isEqualTo(201);

        assertThat(protectedStatus(accountToken("TENANT_ADMINISTRATOR", CUTOFF.minusSeconds(1)))).isEqualTo(401);
        assertThat(protectedStatus(accountToken("TENANT_ADMINISTRATOR", null))).isEqualTo(401);
        assertThat(protectedStatus(accountToken("TENANT_ADMINISTRATOR", CUTOFF))).isEqualTo(200);
        assertThat(protectedStatus(accountToken("TENANT_ADMINISTRATOR", CUTOFF.plusSeconds(60)))).isEqualTo(200);
    }

    @Test
    void aServiceTokenIsNotAffectedByACutoff() throws Exception {
        String id = UUID.randomUUID().toString();
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", account);
        claims.remove("tenantId");
        claims.put("iat", CUTOFF.minusSeconds(3600).getEpochSecond());
        String sameSubject = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        assertThat(deliveredAs(id, CUTOFF, null, null)).isEqualTo(201);

        mvc.perform(deliver(sameSubject, id, event(id, account, CUTOFF, null, null))).andExpect(status().isOk());
        mvc.perform(deliver(serviceToken(), id, event(id, account, CUTOFF, null, null))).andExpect(status().isOk());
    }
}
