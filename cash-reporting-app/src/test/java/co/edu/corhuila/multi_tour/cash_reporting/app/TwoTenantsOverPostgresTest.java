package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * I level through HTTP on the real database (AC-X3, CA-016): two tenants run the same day at the same time and neither
 * sees the other; two writers racing for one day leave one cash or one closure and a clean refusal for the loser.
 */
@RequiresTestDatabase
@SpringBootTest
@AutoConfigureMockMvc
class TwoTenantsOverPostgresTest {

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

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private static String token(String tenant) {
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder write(String tenant, String path, String key, String body) {
        var request = post(BASE + path).header("Authorization", "Bearer " + token(tenant)).header("Idempotency-Key", key);
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder read(String tenant, String path) {
        return get(BASE + path).header("Authorization", "Bearer " + token(tenant));
    }

    private static String expense(String concept, long cents) {
        return "{\"movementType\":\"OPERATIONAL_EXPENSE\",\"paymentMedium\":\"CASH\",\"concept\":\"" + concept
                + "\",\"amountCents\":" + cents + "}";
    }

    private int count(String table, String tenant) {
        return jdbc.queryForObject("select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid",
                Integer.class, tenant);
    }

    /** The whole day of one tenant: open, an expense, an income by transfer, close, reopen, a late expense, close. */
    private void wholeDay(String tenant, String mark, long base, long expense) throws Exception {
        call(write(tenant, DAY + "/open", mark + "-open-key", "{\"openingBaseCents\":" + base + "}"), 200);
        call(write(tenant, DAY + "/movements", mark + "-expense-key", expense("Gasto " + mark, expense)), 201);
        call(write(tenant, DAY + "/movements", mark + "-income-key", "{\"movementType\":\"INCOME\","
                + "\"paymentMedium\":\"TRANSFER\",\"concept\":\"Ingreso " + mark + "\",\"amountCents\":" + 5 * expense + "}"), 201);
        call(write(tenant, DAY + "/close", mark + "-close-key", null), 200);
        call(write(tenant, DAY + "/reopen", mark + "-reopen-key", "{\"reason\":\"Motivo " + mark + "\"}"), 200);
        call(write(tenant, DAY + "/movements", mark + "-late-key", expense("Tarde " + mark, 1_000)), 201);
        call(write(tenant, DAY + "/close", mark + "-close-again-key", null), 200);
    }

    @Test
    void twoTenantsRunTheWholeDayInParallelWithoutLeaks() throws Exception {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        inParallel(List.of(
                () -> { wholeDay(a, "alfa", 100_000, 10_000); return null; },
                () -> { wholeDay(b, "beta", 200_000, 20_000); return null; }));

        assertThat(count("cash_session", a)).isEqualTo(1);
        assertThat(count("cash_session", b)).isEqualTo(1);
        assertThat(count("cash_session_closure", a)).isEqualTo(2);
        assertThat(count("cash_session_closure", b)).isEqualTo(2);
        assertThat(count("cash_movement", a)).isEqualTo(3);
        assertThat(count("cash_movement", b)).isEqualTo(3);

        JsonNode dayA = call(read(a, DAY), 200);
        JsonNode dayB = call(read(b, DAY), 200);
        assertThat(dayA.path("openingBaseCents").asLong()).isEqualTo(100_000);
        assertThat(dayB.path("openingBaseCents").asLong()).isEqualTo(200_000);
        assertThat(dayA.path("expensesCents").asLong()).isEqualTo(11_000);
        assertThat(dayB.path("expensesCents").asLong()).isEqualTo(21_000);
        assertThat(dayA.path("incomeCents").asLong()).isEqualTo(50_000);
        assertThat(dayB.path("incomeCents").asLong()).isEqualTo(100_000);
        assertThat(dayA.path("cashSessionId").asText()).isNotEqualTo(dayB.path("cashSessionId").asText());

        JsonNode movementsA = call(read(a, DAY + "/movements"), 200);
        JsonNode movementsB = call(read(b, DAY + "/movements"), 200);
        assertThat(movementsA.path("data")).hasSize(3);
        assertThat(movementsA.toString()).contains("alfa").doesNotContain("beta");
        assertThat(movementsB.toString()).contains("beta").doesNotContain("alfa");
        JsonNode closuresA = call(read(a, "/cash-closures"), 200);
        JsonNode closuresB = call(read(b, "/cash-closures"), 200);
        assertThat(closuresA.path("data")).hasSize(2);
        assertThat(closuresB.path("data")).hasSize(2);
        assertThat(closuresA.toString()).doesNotContain(dayB.path("cashSessionId").asText());
        assertThat(closuresB.toString()).doesNotContain(dayA.path("cashSessionId").asText());

        // the key of tenant A answers tenant A's own result; tenant B holds none of A's keys
        JsonNode replay = call(write(a, DAY + "/open", "alfa-open-key", "{\"openingBaseCents\":1}"), 200);
        assertThat(replay.path("cashSessionId").asText()).isEqualTo(dayA.path("cashSessionId").asText());
        assertThat(count("idempotency_key", b)).isEqualTo(count("idempotency_key", a));
    }

    private <T> List<T> inParallel(List<Callable<T>> jobs) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> job : jobs) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return job.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Integer> statusesOf(String tenant, String path, String body) throws Exception {
        List<Integer> statuses = inParallel(List.of(
                () -> mvc.perform(write(tenant, path, "race-key-0001", body)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(write(tenant, path, "race-key-0002", body)).andReturn().getResponse().getStatus()));
        return statuses.stream().sorted().toList();
    }

    @Test
    void twoConcurrentOpeningsOverHttpLeaveOneCash() throws Exception {
        String tenant = UUID.randomUUID().toString();

        assertThat(statusesOf(tenant, DAY + "/open", "{\"openingBaseCents\":40000}")).containsExactly(200, 422);

        assertThat(count("cash_session", tenant)).isEqualTo(1);
        assertThat(count("outbox_event", tenant)).isEqualTo(1);
    }

    @Test
    void twoConcurrentClosesOverHttpLeaveOneClosure() throws Exception {
        String tenant = UUID.randomUUID().toString();
        call(write(tenant, DAY + "/open", "race-open-key", "{\"openingBaseCents\":40000}"), 200);

        assertThat(statusesOf(tenant, DAY + "/close", null)).containsExactly(200, 422);

        assertThat(count("cash_session_closure", tenant)).isEqualTo(1);
        assertThat(count("outbox_event", tenant)).isEqualTo(2);
    }
}
