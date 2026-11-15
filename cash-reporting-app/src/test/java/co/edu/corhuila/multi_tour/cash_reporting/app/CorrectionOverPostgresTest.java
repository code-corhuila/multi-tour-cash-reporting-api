package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * I level through HTTP, AC-D1.23 and AC-CJ.5: the real use case and the JPA adapter behind the correction route. The
 * note is stored beside the closure of the closed day, the closure does not change, the replay of the key writes
 * nothing twice and the history of closures lists the note under its closure.
 */
@RequiresTestDatabase
@SpringBootTest
@AutoConfigureMockMvc
class CorrectionOverPostgresTest {

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
    private static final String NOTE = "{\"justification\":\"Se corrige la descripcion de un gasto\"}";

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
        var request = post(BASE + path).header("Authorization", "Bearer " + token()).header("Idempotency-Key", key);
        MvcResult result = mvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode read(String path) throws Exception {
        MvcResult result = mvc.perform(get(BASE + path).header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid",
                Integer.class, tenant);
    }

    private void closedDay() throws Exception {
        send(DAY + "/open", "pg-co-open-1", "{\"openingBaseCents\":40000}", 200);
        send(DAY + "/close", "pg-co-close-1", null, 200);
    }

    @Test
    void aCorrectionIsStoredBesideTheClosureAndChangesNothing() throws Exception {
        closedDay();
        int closures = count("cash_session_closure");

        JsonNode note = send(DAY + "/corrections", "pg-corr-key-1", NOTE, 201);

        assertThat(count("cash_closure_correction")).isEqualTo(1);
        assertThat(count("cash_session_closure")).isEqualTo(closures);
        assertThat(jdbc.queryForObject("select cash_closure_correction_id::text from cash_reporting.cash_closure_correction"
                + " where tenant_id = ?::uuid", String.class, tenant)).isEqualTo(note.path("correctionId").asText());
        assertThat(jdbc.queryForObject("select count(*) from cash_reporting.cash_closure_correction c join"
                + " cash_reporting.cash_session_closure k on k.cash_session_closure_id = c.cash_session_closure_id"
                + " where c.tenant_id = ?::uuid", Integer.class, tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from cash_reporting.cash_session where tenant_id = ?::uuid",
                String.class, tenant)).isEqualTo("CLOSED");
    }

    @Test
    void theReplayOfTheKeyWritesNothingTwiceAndTheHistoryListsTheNote() throws Exception {
        closedDay();
        JsonNode first = send(DAY + "/corrections", "pg-corr-key-2", NOTE, 201);

        assertThat(send(DAY + "/corrections", "pg-corr-key-2", NOTE, 200)).isEqualTo(first);
        assertThat(count("cash_closure_correction")).isEqualTo(1);

        JsonNode history = read("/cash-closures");
        JsonNode corrections = history.path("data").get(0).path("corrections");
        assertThat(corrections).hasSize(1);
        assertThat(corrections.get(0).path("correctionId").asText()).isEqualTo(first.path("correctionId").asText());
    }

    @Test
    void anOpenDayWritesNoNote() throws Exception {
        send(DAY + "/open", "pg-co-open-2", "{\"openingBaseCents\":40000}", 200);

        send(DAY + "/corrections", "pg-corr-key-3", NOTE, 422);

        assertThat(count("cash_closure_correction")).isZero();
        assertThat(count("idempotency_key")).isEqualTo(1);
    }
}
