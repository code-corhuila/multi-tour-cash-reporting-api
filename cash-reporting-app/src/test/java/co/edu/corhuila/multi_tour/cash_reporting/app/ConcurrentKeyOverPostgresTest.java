package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
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
 * I level on the real database, without depending on timing: the same Idempotency-Key arrives twice at once. The
 * second writer is paused right after its read of the key answered "none", the first writer runs to its commit, and
 * only then the second one goes on. The unique key of the tenant and the key has to decide: the loser answers the
 * stored result as a replay (200), writes nothing and leaves one row, one key and one event.
 */
@RequiresTestDatabase
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentKeyOverPostgresTest {

    /** Pauses the first writer whose read of a key finds none, until the test releases it. */
    static final class PauseAfterEmptyKeyRead {
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
        PauseAfterEmptyKeyRead pause() {
            return new PauseAfterEmptyKeyRead();
        }

        @Bean
        static BeanPostProcessor pausingKeys(ObjectProvider<PauseAfterEmptyKeyRead> pause) {
            return new PausingKeys(pause);
        }
    }

    static final class PausingKeys implements BeanPostProcessor, Ordered {
        private final ObjectProvider<PauseAfterEmptyKeyRead> pause;

        PausingKeys(ObjectProvider<PauseAfterEmptyKeyRead> pause) {
            this.pause = pause;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
            if (!(bean instanceof IdempotencyStore)) {
                return bean;
            }
            return Proxy.newProxyInstance(
                    IdempotencyStore.class.getClassLoader(),
                    new Class<?>[] {IdempotencyStore.class},
                    (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(bean, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        if (method.getName().equals("find")) {
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
    private static final String TRANSFER =
            "{\"movementType\":\"INCOME\",\"paymentMedium\":\"TRANSFER\",\"concept\":\"Abono\",\"amountCents\":5000}";
    private static final String NOTE = "{\"justification\":\"Se corrige la descripcion de un gasto\"}";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PauseAfterEmptyKeyRead pause;

    private final String tenant = UUID.randomUUID().toString();

    private String token() {
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MvcResult send(String path, String key, String body) throws Exception {
        var request = post(DAY + path).header("Authorization", "Bearer " + token()).header("Idempotency-Key", key);
        MvcResult result = mvc.perform(
                        body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        ContractCheck.responseMatches(result);
        return result;
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid",
                Integer.class, tenant);
    }

    private Future<MvcResult> pausedWriter(ExecutorService pool, String path, String key, String body)
            throws Exception {
        pause.arm();
        Future<MvcResult> loser = pool.submit(() -> send(path, key, body));
        assertThat(pause.awaitPaused()).as("the second writer read its key and found none").isTrue();
        return loser;
    }

    @Test
    void aMovementKeyRepeatedAtOnceAnswersTheStoredMovementAndWritesOnce() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> loser = pausedWriter(pool, "/movements", "same-key-move-1", TRANSFER);

            MvcResult winner = send("/movements", "same-key-move-1", TRANSFER);
            assertThat(winner.getResponse().getStatus()).as(winner.getResponse().getContentAsString()).isEqualTo(201);
            pause.release();
            MvcResult replay = loser.get(30, TimeUnit.SECONDS);

            assertThat(replay.getResponse().getStatus()).as(replay.getResponse().getContentAsString()).isEqualTo(200);
            JsonNode won = JSON.readTree(winner.getResponse().getContentAsString());
            assertThat(JSON.readTree(replay.getResponse().getContentAsString())).isEqualTo(won);
            assertThat(count("cash_movement")).isEqualTo(1);
            assertThat(count("idempotency_key")).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "select count(*) from cash_reporting.outbox_event where tenant_id = ?::uuid"
                                    + " and event_type = 'CashIncomeRecorded'",
                            Integer.class, tenant))
                    .isEqualTo(1);
        } finally {
            pause.release();
            pool.shutdownNow();
        }
    }

    @Test
    void aCorrectionKeyRepeatedAtOnceAnswersTheStoredCorrectionAndWritesOnce() throws Exception {
        assertThat(send("/open", "same-key-open-1", "{\"openingBaseCents\":40000}").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(send("/close", "same-key-close-1", null).getResponse().getStatus()).isEqualTo(200);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> loser = pausedWriter(pool, "/corrections", "same-key-corr-1", NOTE);

            MvcResult winner = send("/corrections", "same-key-corr-1", NOTE);
            assertThat(winner.getResponse().getStatus()).as(winner.getResponse().getContentAsString()).isEqualTo(201);
            pause.release();
            MvcResult replay = loser.get(30, TimeUnit.SECONDS);

            assertThat(replay.getResponse().getStatus()).as(replay.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(JSON.readTree(replay.getResponse().getContentAsString()))
                    .isEqualTo(JSON.readTree(winner.getResponse().getContentAsString()));
            assertThat(count("cash_closure_correction")).isEqualTo(1);
            assertThat(count("idempotency_key")).isEqualTo(3);
        } finally {
            pause.release();
            pool.shutdownNow();
        }
    }
}
