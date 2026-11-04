package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryIdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryOutbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** What the C tests of the writes share: a tenant per test, tokens, the request builder and the contract check. */
abstract class WriteControllersSupport {

    static final String BASE = "/api/v1/cash-reporting";
    static final ObjectMapper JSON = new ObjectMapper();
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final BusinessDate TODAY = BusinessDate.of(LocalDate.of(2026, 11, 3));
    static final BusinessDate YESTERDAY = BusinessDate.of(LocalDate.of(2026, 11, 2));
    static final String KEY = "write-key-0001";

    @Autowired
    MockMvc mvc;
    @Autowired
    InMemoryCashSessionRepository sessions;
    @Autowired
    InMemoryCashMovementRepository movements;
    @Autowired
    InMemoryCashClosureRepository closures;
    @Autowired
    InMemoryCashReopeningRepository reopenings;
    @Autowired
    InMemoryIdempotencyStore keys;
    @Autowired
    InMemoryOutbox outbox;

    final String tenant = UUID.randomUUID().toString();

    String token(String role, String... permissions) {
        Map<String, Object> claims = TestKeys.claims(role, permissions);
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    /** A write by an administrator: key and body are optional so a test can leave either one out. */
    MockHttpServletRequestBuilder write(String path, String key, String body) {
        return write(path, token("TENANT_ADMINISTRATOR"), key, body);
    }

    MockHttpServletRequestBuilder write(String path, String token, String key, String body) {
        MockHttpServletRequestBuilder request = post(BASE + path).header("Authorization", "Bearer " + token);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    JsonNode answered(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    void seedOpen(BusinessDate date, long baseCents) {
        sessions.seed(CashSession.openNew(tenant, date, BOGOTA,
                new CashSession.Opening(Cents.of(baseCents), TestKeys.NOW, TestKeys.ACCOUNT), date));
    }

    long eventsOfTheTenant() {
        return outbox.events().stream().filter(event -> event.tenantId().equals(tenant)).count();
    }

    void assertNothingWasWritten(String key) {
        assertThat(sessions.find(tenant, TODAY)).isEmpty();
        assertThat(keys.find(tenant, key)).isEmpty();
        assertThat(eventsOfTheTenant()).isZero();
    }

    static String errorOf(JsonNode body) {
        return body.path("error").asText();
    }

    static String firstField(JsonNode body) {
        return body.path("details").get(0).path("field").asText();
    }
}
