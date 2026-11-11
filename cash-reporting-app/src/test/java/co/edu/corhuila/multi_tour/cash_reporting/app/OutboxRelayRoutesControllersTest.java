package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * C level, the three outbox routes of job 7 (HU-WRK-007, G-RL7-1; the same routes as commercial-reservation, ADR-004 D4): only a SERVICE_TOKEN,
 * across tenants, and only the delivery state of the row moves. The routes are not in the approved contract yet (PROPOSED), so the response is read
 * here and not validated against it.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class OutboxRelayRoutesControllersTest extends WriteControllersSupport {

    private static final String OUTBOX = BASE + "/internal/outbox";
    private static final String USED = "PlatformExceptionalAccessUsed";


    private String serviceToken() {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", "multi-tour-worker-event-relay");
        claims.remove("tenantId");
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int status) throws Exception {
        var result = mvc.perform(request.header("Authorization", "Bearer " + serviceToken())).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private String pendingEventOfTheTenant() throws Exception {
        outbox.append(tenant, USED, UUID.randomUUID().toString(), Map.of("area", "CASH"));
        JsonNode page = call(get(OUTBOX).queryParam("status", "PENDING").queryParam("limit", "100"), 200);
        String id = null;
        for (JsonNode event : page.path("data")) {
            if (event.path("tenantId").asText().equals(tenant)) id = event.path("eventId").asText();
        }
        return id;
    }

    @Test
    void theListCarriesTheEnvelopeOfThePendingEventsAcrossTenants() throws Exception {
        String id = pendingEventOfTheTenant();

        JsonNode page = call(get(OUTBOX).queryParam("status", "PENDING").queryParam("limit", "100"), 200);

        assertThat(page.path("meta").path("total").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode event = null;
        for (JsonNode candidate : page.path("data")) {
            if (candidate.path("eventId").asText().equals(id)) event = candidate;
        }
        assertThat(event).isNotNull();
        assertThat(event.path("eventType").asText()).isEqualTo(USED);
        assertThat(event.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.path("tenantId").asText()).isEqualTo(tenant);
        assertThat(event.path("status").asText()).isEqualTo("PENDING");
        assertThat(event.path("payload").path("area").asText()).isEqualTo("CASH");
        assertThat(event.path("occurredAt").asText()).isNotBlank();
    }

    @Test
    void aPublishedEventLeavesTheListAndARepeatAnswersTheSameState() throws Exception {
        String id = pendingEventOfTheTenant();

        JsonNode published = call(post(OUTBOX + "/" + id + "/published"), 200);
        JsonNode again = call(post(OUTBOX + "/" + id + "/published"), 200);

        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.path("publishedAt").asText()).isNotBlank();
        assertThat(again).isEqualTo(published);
        List<String> ids = call(get(OUTBOX).queryParam("limit", "100"), 200).path("data").findValuesAsText("eventId");
        assertThat(ids).doesNotContain(id);
    }

    @Test
    void aFailedAttemptIsCountedAndItsReasonIsRequired() throws Exception {
        String id = pendingEventOfTheTenant();

        JsonNode counted = call(post(OUTBOX + "/" + id + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{\"error\":\"consumer answered 500\"}"), 200);

        assertThat(counted.path("attempts").asInt()).isEqualTo(1);
        assertThat(counted.path("status").asText()).isEqualTo("PENDING");
        call(post(OUTBOX + "/" + id + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"), 400);
        call(post(OUTBOX + "/" + id + "/attempts").contentType(MediaType.APPLICATION_JSON).content("{\"error\":\"  \"}"), 400);
    }

    @Test
    void anUnknownEventIsNotFoundAndAnIdentifierThatIsNotAUuidIsABadRequest() throws Exception {
        call(post(OUTBOX + "/" + UUID.randomUUID() + "/published"), 404);
        call(post(OUTBOX + "/not-a-uuid/published"), 400);
    }

    @Test
    void aPersonAnExceptionalTokenAndNoTokenAreRefused() throws Exception {
        String id = pendingEventOfTheTenant();

        assertThat(mvc.perform(get(OUTBOX).header("Authorization", "Bearer " + token("TENANT_ADMINISTRATOR"))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post(OUTBOX + "/" + id + "/published").header("Authorization", "Bearer " + token("TENANT_ADMINISTRATOR"))).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mvc.perform(get(OUTBOX)).andReturn().getResponse().getStatus()).isEqualTo(401);
        Map<String, Object> claims = TestKeys.claims("PLATFORM_ADMINISTRATOR");
        claims.put("tenantId", tenant);
        claims.put("access", "exceptional");
        claims.put("scope", List.of("CASH"));
        claims.put("authorizationId", UUID.randomUUID().toString());
        String exceptional = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        assertThat(mvc.perform(get(OUTBOX).header("Authorization", "Bearer " + exceptional)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(outbox.find(id).orElseThrow().status()).isEqualTo("PENDING");
    }
}
