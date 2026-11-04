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
 * I level through HTTP: the real use cases and JPA adapters behind the movement, closing and reopening routes, with the
 * fixed clock (2026-11-03 07:00 in Bogota). It proves each write commits its rows, its key and its event together.
 */
@RequiresTestDatabase
@SpringBootTest
@AutoConfigureMockMvc
class MovementsCloseReopenOverPostgresTest {

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
    private static final String EXPENSE = "{\"movementType\":\"OPERATIONAL_EXPENSE\",\"paymentMedium\":\"CASH\","
            + "\"concept\":\"Compra\",\"amountCents\":10000}";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private final String tenant = UUID.randomUUID().toString();

    private String token() {
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private JsonNode send(String path, String key, String body, int status) throws Exception {
        var request = post(BASE + path).header("Authorization", "Bearer " + token());
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        MvcResult result = mvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid",
                Integer.class, tenant);
    }

    private String statusOfTheDay() {
        return jdbc.queryForObject("select status from cash_reporting.cash_session where tenant_id = ?::uuid"
                + " and business_date = date '2026-11-03'", String.class, tenant);
    }

    @Test
    void aMovementIsStoredWithItsKeyAndItsEventInOneTransaction() throws Exception {
        send(DAY + "/open", "pg-mv-open-1", "{\"openingBaseCents\":40000}", 200);
        assertThat(count("outbox_event")).isEqualTo(1);

        JsonNode movement = send(DAY + "/movements", "pg-mv-key-1", EXPENSE, 201);
        assertThat(count("cash_movement")).isEqualTo(1);
        assertThat(count("idempotency_key")).isEqualTo(2);
        assertThat(count("outbox_event")).isEqualTo(2);

        assertThat(send(DAY + "/movements", "pg-mv-key-1", EXPENSE, 200)).isEqualTo(movement);
        assertThat(count("cash_movement")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(2);

        assertThat(send(DAY + "/movements", "pg-mv-key-2", EXPENSE.replace("\"amountCents\":10000", "\"amountCents\":0"), 400)
                .path("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(count("cash_movement")).isEqualTo(1);
        assertThat(count("idempotency_key")).isEqualTo(2);
    }

    @Test
    void aTransferWithoutCashCreatesTheDayAndCashWithoutOpeningWritesNothing() throws Exception {
        send(DAY + "/movements", "pg-mv-key-3", EXPENSE, 422);
        assertThat(count("cash_session")).isZero();
        assertThat(count("idempotency_key")).isZero();
        assertThat(count("outbox_event")).isZero();

        send(DAY + "/movements", "pg-mv-key-4", EXPENSE.replace("\"CASH\"", "\"TRANSFER\""), 201);
        assertThat(count("cash_session")).isEqualTo(1);
        assertThat(count("cash_movement")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    void closingKeepsThePhotoAndReopeningKeepsTheClosure() throws Exception {
        send(DAY + "/open", "pg-cl-open-1", "{\"openingBaseCents\":40000}", 200);
        send(DAY + "/movements", "pg-cl-mv-1", EXPENSE, 201);

        JsonNode closed = send(DAY + "/close", "pg-close-key-1", null, 200);
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");
        assertThat(statusOfTheDay()).isEqualTo("CLOSED");
        assertThat(count("cash_session_closure")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select closing_balance_cents from cash_reporting.cash_session_closure"
                + " where tenant_id = ?::uuid", Long.class, tenant)).isEqualTo(closed.path("totalCents").asLong());
        assertThat(count("outbox_event")).isEqualTo(3);
        assertThat(send(DAY + "/close", "pg-close-key-1", null, 200)).isEqualTo(closed);
        assertThat(count("cash_session_closure")).isEqualTo(1);
        assertThat(send(DAY + "/close", "pg-close-key-2", null, 422).path("error").asText()).isEqualTo("ALREADY_CLOSED");

        send(DAY + "/reopen", "pg-reopen-key-1", "{\"reason\":\"Falto un pago\"}", 200);
        assertThat(statusOfTheDay()).isEqualTo("OPEN");
        assertThat(count("cash_session_closure")).as("the closure stays as history").isEqualTo(1);
        assertThat(count("cash_session_reopening")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(4);
        assertThat(send(DAY + "/reopen", "pg-reopen-key-2", "{}", 400).path("error").asText()).isEqualTo("REASON_REQUIRED");
        assertThat(count("cash_session_reopening")).isEqualTo(1);

        send(DAY + "/close", "pg-close-key-3", null, 200);
        assertThat(count("cash_session_closure")).as("a second closing is another closure").isEqualTo(2);
    }
}
