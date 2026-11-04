package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * I level through HTTP, DP-A1-3 of HU-CSH-001: the key of a movement used to open the cash answers 400 on
 * Idempotency-Key (it was a 500 because the movement id was read as a date) and the stored rows stay as they were.
 */
@RequiresTestDatabase
@SpringBootTest
@AutoConfigureMockMvc
class KeyOfAnotherOperationOverPostgresTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TestKeys.NOW, ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PersistenceTestBase.database(registry);
    }

    private static final String BASE = "/api/v1/cash-reporting";
    private static final String DAY = "/cash-days/2026-11-03";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TRANSFER = "{\"movementType\":\"OPERATIONAL_EXPENSE\",\"paymentMedium\":\"TRANSFER\","
            + "\"concept\":\"Compra\",\"amountCents\":10000}";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private final String tenant = UUID.randomUUID().toString();

    private JsonNode send(String path, String key, String body, int status) throws Exception {
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("tenantId", tenant);
        String token = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        var request = post(BASE + path).header("Authorization", "Bearer " + token).header("Idempotency-Key", key);
        MvcResult result = mvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid",
                Integer.class, tenant);
    }

    @Test
    void aMovementKeyUsedToOpenAnswers400AndWritesNothing() throws Exception {
        send(DAY + "/movements", "pg-cross-key-1", TRANSFER, 201);
        int keys = count("idempotency_key");
        int events = count("outbox_event");

        JsonNode refused = send(DAY + "/open", "pg-cross-key-1", "{\"openingBaseCents\":5}", 400);

        assertThat(refused.path("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(refused.path("details").get(0).path("field").asText()).isEqualTo("Idempotency-Key");
        assertThat(count("idempotency_key")).isEqualTo(keys);
        assertThat(count("outbox_event")).isEqualTo(events);
        assertThat(count("cash_movement")).isEqualTo(1);
    }

    @Test
    void aDayKeyUsedToCloseReopenOrRegisterAMovementAnswers400() throws Exception {
        send(DAY + "/open", "pg-cross-key-2", "{\"openingBaseCents\":40000}", 200);

        assertThat(send(DAY + "/movements", "pg-cross-key-2", TRANSFER, 400)
                .path("details").get(0).path("field").asText()).isEqualTo("Idempotency-Key");
        assertThat(count("cash_movement")).isZero();

        send(DAY + "/movements", "pg-cross-key-3", TRANSFER, 201);
        assertThat(send(DAY + "/close", "pg-cross-key-3", null, 400)
                .path("details").get(0).path("field").asText()).isEqualTo("Idempotency-Key");
        assertThat(count("cash_session_closure")).isZero();

        send(DAY + "/close", "pg-cross-key-4", null, 200);
        assertThat(send(DAY + "/reopen", "pg-cross-key-3", "{\"reason\":\"Falto un pago\"}", 400)
                .path("details").get(0).path("field").asText()).isEqualTo("Idempotency-Key");
        assertThat(count("cash_session_reopening")).isZero();
    }
}
