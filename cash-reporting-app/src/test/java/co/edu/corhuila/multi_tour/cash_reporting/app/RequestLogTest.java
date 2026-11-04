package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CorrelationFilter;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** One structured line per request, with the ids and the outcome and nothing the story keeps private (RNF-A1.4 A1.6). */
@SpringBootTest
@AutoConfigureMockMvc
class RequestLogTest {

    @TestConfiguration
    static class Fixed {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TestKeys.NOW, ZoneOffset.UTC);
        }
    }

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CorrelationFilter.class);

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void capture() {
        lines.start();
        logger.addAppender(lines);
    }

    @AfterEach
    void release() {
        logger.detachAppender(lines);
    }

    @Test
    void oneLinePerRequestCarriesTheTraceTheTenantAndTheOutcome() throws Exception {
        String token = TestKeys.token("TENANT_ADMINISTRATOR");
        mvc.perform(get("/api/v1/cash-reporting/_probe/violation/already-open")
                .header("Authorization", "Bearer " + token).header("X-Correlation-Id", "log-1"));
        List<String> messages = lines.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("traceId=log-1", "tenantId=" + TestKeys.TENANT,
                "operation=GET /api/v1/cash-reporting/_probe/violation/{kind}", "status=422", "outcome=ALREADY_OPEN",
                "durationMs=");
    }

    @Test
    void aRefusedTokenIsLoggedWithoutTenantAndWithoutTheToken() throws Exception {
        mvc.perform(get("/api/v1/cash-reporting/cash-days/current").header("Authorization", "Bearer abc.def.ghi"));
        assertThat(lines.list).hasSize(1);
        String line = lines.list.get(0).getFormattedMessage();
        assertThat(line).contains("status=401", "outcome=UNAUTHORIZED").doesNotContain("abc.def.ghi");
    }

    @Test
    void theLineNeverCarriesTheBodyTheTokenOrAnAmount() throws Exception {
        String token = TestKeys.token("TENANT_ADMINISTRATOR");
        mvc.perform(post("/api/v1/cash-reporting/_probe/echo").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"concept\":\"Pago guia Maria Lopez 250000\"}"));
        assertThat(lines.list).hasSize(1);
        assertThat(lines.list.get(0).getFormattedMessage()).doesNotContain("Maria", "Lopez", "250000", token);
    }
}
