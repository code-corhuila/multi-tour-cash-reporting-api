package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * I level on the real database, without depending on timing: the second writer is paused right after its locked read
 * answered "no cash" (FOR UPDATE on zero rows locks nothing), the first writer runs to its commit, and only then the
 * second one goes on. The unique key of the tenant and date has to decide, not a second read of the day (D-08, AC3).
 */
@RequiresTestDatabase
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentCreationOverPostgresTest {

    /** Pauses the first writer that reads "no cash" until the test releases it. */
    static final class PauseAfterEmptyRead {
        private volatile boolean armed;
        private volatile CountDownLatch reached = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void arm() {
            reached = new CountDownLatch(1);
            release = new CountDownLatch(1);
            armed = true;
        }

        boolean awaitPaused() throws InterruptedException {
            return reached.await(20, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }

        void afterRead(Object result) {
            if (armed && result instanceof Optional<?> found && found.isEmpty()) {
                armed = false;
                reached.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The test never released the paused writer");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TestKeys.NOW, ZoneOffset.UTC);
        }

        @Bean
        PauseAfterEmptyRead pause() {
            return new PauseAfterEmptyRead();
        }

        /** Wraps the transactional repository (after its proxy exists) so the locked read can be paused. */
        @Bean
        static BeanPostProcessor pausingSessions(ObjectProvider<PauseAfterEmptyRead> pause) {
            return new PausingSessions(pause);
        }
    }

    static final class PausingSessions implements BeanPostProcessor, Ordered {
        private final ObjectProvider<PauseAfterEmptyRead> pause;

        PausingSessions(ObjectProvider<PauseAfterEmptyRead> pause) {
            this.pause = pause;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
            if (!(bean instanceof CashSessionRepository)) {
                return bean;
            }
            return Proxy.newProxyInstance(
                    CashSessionRepository.class.getClassLoader(),
                    new Class<?>[] {CashSessionRepository.class},
                    (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(bean, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        if (method.getName().equals("findForUpdate")) {
                            pause.getObject().afterRead(result);
                        }
                        return result;
                    });
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PersistenceTestBase.database(registry);
    }

    private static final String DAY = "/api/v1/cash-reporting/cash-days/2026-11-03";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PauseAfterEmptyRead pause;

    private static String token(String tenant) {
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MvcResult send(String tenant, String path, String key, String body) throws Exception {
        MvcResult result = mvc.perform(post(DAY + path)
                        .header("Authorization", "Bearer " + token(tenant))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        ContractCheck.responseMatches(result);
        return result;
    }

    private static String transfer(String concept) {
        return "{\"movementType\":\"INCOME\",\"paymentMedium\":\"TRANSFER\",\"concept\":\"" + concept
                + "\",\"amountCents\":5000}";
    }

    private int count(String table, String tenant) {
        return jdbc.queryForObject("select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid",
                Integer.class, tenant);
    }

    private int events(String tenant, String type) {
        return jdbc.queryForObject(
                "select count(*) from cash_reporting.outbox_event where tenant_id = ?::uuid and event_type = ?",
                Integer.class, tenant, type);
    }

    /** Runs the paused writer B, waits until it stands after its empty read, and returns it with its future. */
    private Future<MvcResult> pausedWriter(ExecutorService pool, String tenant, String path, String key, String body)
            throws Exception {
        pause.arm();
        Future<MvcResult> loser = pool.submit(() -> send(tenant, path, key, body));
        assertThat(pause.awaitPaused()).as("the second writer reached its empty read").isTrue();
        return loser;
    }

    @Test
    void anOpeningThatReadNoCashButLostTheCommitIsRefusedAlreadyOpenAndKeepsTheWinnersBase() throws Exception {
        String tenant = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> loser =
                    pausedWriter(pool, tenant, "/open", "race-loser-key-1", "{\"openingBaseCents\":30000}");

            MvcResult winner = send(tenant, "/open", "race-winner-key-1", "{\"openingBaseCents\":70000}");
            assertThat(winner.getResponse().getStatus()).as(winner.getResponse().getContentAsString()).isEqualTo(200);
            pause.release();
            MvcResult refused = loser.get(30, TimeUnit.SECONDS);

            assertThat(refused.getResponse().getStatus()).as(refused.getResponse().getContentAsString()).isEqualTo(422);
            JsonNode body = JSON.readTree(refused.getResponse().getContentAsString());
            assertThat(body.path("error").asText()).isEqualTo("ALREADY_OPEN");
            assertThat(jdbc.queryForList(
                            "select opening_base_cents from cash_reporting.cash_session where tenant_id = ?::uuid",
                            Long.class, tenant))
                    .containsExactly(70_000L);
            assertThat(count("outbox_event", tenant)).isEqualTo(1);
            assertThat(count("idempotency_key", tenant)).isEqualTo(1);
        } finally {
            pause.release();
            pool.shutdownNow();
        }
    }

    @Test
    void aTransferThatReadNoCashButLostTheCommitRecordsOnTheDayTheWinnerCreated() throws Exception {
        String tenant = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> loser =
                    pausedWriter(pool, tenant, "/movements", "race-loser-key-2", transfer("Perdedor"));

            MvcResult winner = send(tenant, "/movements", "race-winner-key-2", transfer("Ganador"));
            assertThat(winner.getResponse().getStatus()).as(winner.getResponse().getContentAsString()).isEqualTo(201);
            pause.release();
            MvcResult recorded = loser.get(30, TimeUnit.SECONDS);

            assertThat(recorded.getResponse().getStatus()).as(recorded.getResponse().getContentAsString()).isEqualTo(201);
            assertThat(count("cash_session", tenant)).isEqualTo(1);
            assertThat(count("cash_movement", tenant)).isEqualTo(2);
            assertThat(events(tenant, "CashSessionAutoCreated")).as("the day is created once").isEqualTo(1);
            assertThat(count("idempotency_key", tenant)).isEqualTo(2);
        } finally {
            pause.release();
            pool.shutdownNow();
        }
    }

    @Test
    void anOpeningThatReadNoCashButLostToAnAutomaticCreationOpensTheDayThatNowExists() throws Exception {
        String tenant = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> loser =
                    pausedWriter(pool, tenant, "/open", "race-loser-key-3", "{\"openingBaseCents\":30000}");

            MvcResult winner = send(tenant, "/movements", "race-winner-key-3", transfer("Automatica"));
            assertThat(winner.getResponse().getStatus()).as(winner.getResponse().getContentAsString()).isEqualTo(201);
            pause.release();
            MvcResult opened = loser.get(30, TimeUnit.SECONDS);

            assertThat(opened.getResponse().getStatus()).as(opened.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(jdbc.queryForList(
                            "select opening_base_cents from cash_reporting.cash_session where tenant_id = ?::uuid",
                            Long.class, tenant))
                    .containsExactly(30_000L);
            assertThat(events(tenant, "CashSessionAutoCreated")).isEqualTo(1);
            assertThat(events(tenant, "CashSessionOpened")).isEqualTo(1);
        } finally {
            pause.release();
            pool.shutdownNow();
        }
    }
}
