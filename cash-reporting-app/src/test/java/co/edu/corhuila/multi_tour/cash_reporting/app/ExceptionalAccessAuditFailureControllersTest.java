package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ExceptionalAccessAudit;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.OutboxExceptionalAccessAudit;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11): the audit of the use of an exceptional access is mandatory and fails closed: if the outbox cannot be written the read answers 500 with the standard
 * error and no data. No database: the outbox is a writer that throws.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({InMemoryWorld.class, ExceptionalAccessAuditFailureControllersTest.Failing.class})
class ExceptionalAccessAuditFailureControllersTest {

    @TestConfiguration
    static class Failing {
        @Bean
        @Primary
        ExceptionalAccessAudit failingAudit() {
            return new OutboxExceptionalAccessAudit((tenantId, eventType, aggregateId, payload) -> {
                throw new IllegalStateException("the outbox is down");
            }, Clock.fixed(TestKeys.NOW, ZoneOffset.UTC));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void aReadWhoseUseCannotBeAuditedAnswersInternalErrorWithNoData() throws Exception {
        Map<String, Object> claims = TestKeys.claims("PLATFORM_ADMINISTRATOR");
        claims.put("tenantId", UUID.randomUUID().toString());
        claims.put("access", "exceptional");
        claims.put("scope", List.of("CASH"));
        claims.put("authorizationId", UUID.randomUUID().toString());
        String bearer = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");

        MvcResult result = mvc.perform(get("/api/v1/cash-reporting/cash-days").header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).contains("INTERNAL_ERROR").doesNotContain("outbox").doesNotContain("items");
    }
}
