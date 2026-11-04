package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
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
 * I level through HTTP: the real use cases and JPA adapters behind the opening and base routes, with the fixed clock
 * (2026-11-03 07:00 in Bogota). It proves the wiring of the composition root and the one transaction of day, key and event.
 */
@RequiresTestDatabase
@SpringBootTest
@AutoConfigureMockMvc
class OpenAndBaseOverPostgresTest {

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
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CashSessionRepository sessions;

    private final String tenant = UUID.randomUUID().toString();

    private String token(String role, String... permissions) {
        Map<String, Object> claims = TestKeys.claims(role, permissions);
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private JsonNode send(String path, String token, String key, String body, int status) throws Exception {
        var request = post(BASE + path).header("Authorization", "Bearer " + token);
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

    @Test
    void openingAndAdjustingWriteTheDayTheKeyAndTheEventInOneTransaction() throws Exception {
        String admin = token("TENANT_ADMINISTRATOR");

        JsonNode opened = send("/cash-days/2026-11-03/open", admin, "pg-open-key-1", "{\"openingBaseCents\":40000}", 200);
        assertThat(opened.path("openingBaseCents").asLong()).isEqualTo(40_000);
        assertThat(count("cash_session")).isEqualTo(1);
        assertThat(count("idempotency_key")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);

        assertThat(send("/cash-days/2026-11-03/open", admin, "pg-open-key-1", "{\"openingBaseCents\":1}", 200)).isEqualTo(opened);
        assertThat(count("cash_session")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
        assertThat(send("/cash-days/2026-11-03/open", admin, "pg-open-key-2", null, 422).path("error").asText())
                .isEqualTo("ALREADY_OPEN");

        JsonNode adjusted = send("/cash-days/2026-11-03/adjust-base", admin, null, "{\"openingBaseCents\":45000}", 200);
        assertThat(adjusted.path("openingBaseCents").asLong()).isEqualTo(45_000);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void aCollaboratorOpensWithTheBaseOfTheLatestOpeningAndCannotTypeIt() throws Exception {
        sessions.save(tenant, PersistenceTestBase.opened(tenant, PersistenceTestBase.day(2), TestKeys.ACCOUNT)
                .adjustBase(Cents.of(65_000)));
        String collaborator = token("OPERATIONAL_COLLABORATOR", TestKeys.MANAGE_CASH);

        send("/cash-days/2026-11-03/open", collaborator, "pg-open-key-3", "{\"openingBaseCents\":1}", 403);
        assertThat(count("cash_session")).isEqualTo(1);
        assertThat(count("idempotency_key")).isZero();

        JsonNode opened = send("/cash-days/2026-11-03/open", collaborator, "pg-open-key-4", null, 200);
        assertThat(opened.path("openingBaseCents").asLong()).isEqualTo(65_000);
        assertThat(count("cash_session")).isEqualTo(2);
    }
}
