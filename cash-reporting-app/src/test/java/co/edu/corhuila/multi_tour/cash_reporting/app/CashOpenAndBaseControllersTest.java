package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryIdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryOutbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * C level, opening and base of HU-CSH-001: every response is validated against openapi/openapi.yaml. The repositories
 * are the in-memory fakes; the fixed clock says it is 2026-11-03 07:00 in Bogota. Each test has a tenant of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class CashOpenAndBaseControllersTest {

    private static final String BASE = "/api/v1/cash-reporting";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final BusinessDate TODAY = BusinessDate.of(LocalDate.of(2026, 11, 3));
    private static final BusinessDate YESTERDAY = BusinessDate.of(LocalDate.of(2026, 11, 2));
    private static final String KEY = "open-key-0001";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private InMemoryCashSessionRepository sessions;
    @Autowired
    private InMemoryCashMovementRepository movements;
    @Autowired
    private InMemoryIdempotencyStore keys;
    @Autowired
    private InMemoryOutbox outbox;

    private final String tenant = UUID.randomUUID().toString();

    private String token(String role, String... permissions) {
        Map<String, Object> claims = TestKeys.claims(role, permissions);
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MockHttpServletRequestBuilder open(String date, String role, String key, String body, String... permissions) {
        MockHttpServletRequestBuilder request = post(BASE + "/cash-days/" + date + "/open")
                .header("Authorization", "Bearer " + token(role, permissions));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder adjust(String date, String role, String body, String... permissions) {
        MockHttpServletRequestBuilder request = post(BASE + "/cash-days/" + date + "/adjust-base")
                .header("Authorization", "Bearer " + token(role, permissions));
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode answered(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private CashSession seeded(BusinessDate date, long baseCents) {
        CashSession session = CashSession.openNew(tenant, date, BOGOTA,
                new CashSession.Opening(Cents.of(baseCents), TestKeys.NOW, TestKeys.ACCOUNT), date);
        sessions.seed(session);
        return session;
    }

    private long eventsOfTheTenant() {
        return outbox.events().stream().filter(event -> event.tenantId().equals(tenant)).count();
    }

    private void assertNothingWasWritten() {
        assertThat(sessions.find(tenant, TODAY)).isEmpty();
        assertThat(keys.find(tenant, KEY)).isEmpty();
        assertThat(eventsOfTheTenant()).isZero();
    }

    @Test
    void anAdministratorOpensTheCashWithTheBaseTheyType() throws Exception {
        JsonNode day = answered(open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, "{\"openingBaseCents\":30000000}"), 200);

        assertThat(day.path("businessDate").asText()).isEqualTo("2026-11-03");
        assertThat(day.path("status").asText()).isEqualTo("OPEN");
        assertThat(day.path("origin").asText()).isEqualTo("OPERATIONAL_OPENING");
        assertThat(day.path("hasOperationalOpening").asBoolean()).isTrue();
        assertThat(day.path("openingBaseCents").asLong()).isEqualTo(30_000_000);
        assertThat(day.path("totalCents").asLong()).isEqualTo(30_000_000);
        assertThat(day.path("expectedCashCents").asLong()).isEqualTo(30_000_000);
        assertThat(day.path("openedBy").path("userId").asText()).isEqualTo(TestKeys.ACCOUNT);
        assertThat(sessions.find(tenant, TODAY)).get().extracting(CashSession::origin).isEqualTo(Origin.OPERATIONAL_OPENING);
        assertThat(outbox.events()).filteredOn(event -> event.tenantId().equals(tenant))
                .singleElement().satisfies(event -> assertThat(event.eventType()).isEqualTo("CashSessionOpened"));
    }

    @Test
    void aCollaboratorOpensWithTheParametrizedBaseOfTheLatestOpening() throws Exception {
        seeded(YESTERDAY, 12_000_000);
        seeded(BusinessDate.of(LocalDate.of(2026, 10, 20)), 99_000_000);

        JsonNode day = answered(open("2026-11-03", "OPERATIONAL_COLLABORATOR", KEY, null, TestKeys.MANAGE_CASH), 200);

        assertThat(day.path("openingBaseCents").asLong()).isEqualTo(12_000_000);
        assertThat(day.path("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void aCollaboratorOpensWithZeroWhenTheTenantNeverOpened() throws Exception {
        JsonNode day = answered(open("2026-11-03", "OPERATIONAL_COLLABORATOR", KEY, "{}", TestKeys.MANAGE_CASH), 200);

        assertThat(day.path("openingBaseCents").asLong()).isZero();
        assertThat(day.path("totalCents").asLong()).isZero();
    }

    @Test
    void aCollaboratorSendingTheBaseIsForbiddenBeforeWritingAnything() throws Exception {
        JsonNode refused = answered(
                open("2026-11-03", "OPERATIONAL_COLLABORATOR", KEY, "{\"openingBaseCents\":1}", TestKeys.MANAGE_CASH), 403);

        assertThat(refused.path("error").asText()).isEqualTo("FORBIDDEN");
        assertNothingWasWritten();
    }

    @Test
    void repeatingTheKeyAnswers200WithTheSameDayAndWritesNothingTwice() throws Exception {
        JsonNode first = answered(open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, "{\"openingBaseCents\":5000}"), 200);
        int writes = sessions.writes();

        JsonNode again = answered(open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, "{\"openingBaseCents\":9999}"), 200);

        assertThat(again).isEqualTo(first);
        assertThat(sessions.writes()).isEqualTo(writes);
        assertThat(eventsOfTheTenant()).isEqualTo(1);
    }

    @Test
    void theSameKeyOfAnotherTenantIsAnotherOpening() throws Exception {
        answered(open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, "{\"openingBaseCents\":5000}"), 200);
        String other = UUID.randomUUID().toString();
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("tenantId", other);
        String otherToken = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");

        MvcResult result = mvc.perform(post(BASE + "/cash-days/2026-11-03/open").header("Authorization", "Bearer " + otherToken)
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBaseCents\":7000}")).andExpect(status().isOk()).andReturn();

        ContractCheck.responseMatches(result);
        assertThat(JSON.readTree(result.getResponse().getContentAsString()).path("openingBaseCents").asLong()).isEqualTo(7000);
        assertThat(sessions.find(tenant, TODAY).orElseThrow().opening().base()).isEqualTo(Cents.of(5000));
    }

    @Test
    void theStateRulesOfTheOpeningAnswer422() throws Exception {
        answered(open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, null), 200);

        assertThat(answered(open("2026-11-03", "TENANT_ADMINISTRATOR", "another-key-01", null), 422).path("error").asText())
                .isEqualTo("ALREADY_OPEN");
        assertThat(answered(open("2026-11-02", "TENANT_ADMINISTRATOR", "past-date-key", null), 422).path("error").asText())
                .isEqualTo("BUSINESS_RULE_VIOLATION");

        sessions.seed(sessions.find(tenant, TODAY).orElseThrow().close(ClosingMode.MANUAL));
        assertThat(answered(open("2026-11-03", "TENANT_ADMINISTRATOR", "closed-day-key", null), 422).path("error").asText())
                .isEqualTo("ALREADY_CLOSED");
    }

    @Test
    void aBodyCarryingTenantIdIsRejected() throws Exception {
        seeded(TODAY, 1_000);
        List<MockHttpServletRequestBuilder> requests = List.of(
                open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, "{\"tenantId\":\"" + tenant + "\"}"),
                open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, "{\"openingBaseCents\":5,\"tenantId\":\"" + tenant + "\"}"),
                adjust("2026-11-03", "TENANT_ADMINISTRATOR", "{\"openingBaseCents\":5,\"tenantId\":\"" + tenant + "\"}"));
        for (MockHttpServletRequestBuilder request : requests) {
            JsonNode refused = answered(request, 400);
            assertThat(refused.path("error").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(refused.path("details").get(0).path("field").asText()).isEqualTo("tenantId");
        }
        JsonNode extra = answered(adjust("2026-11-03", "TENANT_ADMINISTRATOR", "{\"openingBaseCents\":5,\"currency\":\"COP\"}"), 400);
        assertThat(extra.path("details").get(0).path("field").asText()).isEqualTo("currency");
        assertThat(sessions.find(tenant, TODAY).orElseThrow().opening().base()).isEqualTo(Cents.of(1_000));
        assertThat(eventsOfTheTenant()).isZero();
    }

    @Test
    void aMissingOrMalformedKeyIsRejected() throws Exception {
        for (String key : new String[] {null, "", "        ", "short-7", "k".repeat(129)}) {
            JsonNode refused = answered(open("2026-11-03", "TENANT_ADMINISTRATOR", key, "{\"openingBaseCents\":5}"), 400);
            assertThat(refused.path("error").asText()).as("key [" + key + "]").isEqualTo("VALIDATION_ERROR");
            assertThat(refused.path("details").get(0).path("field").asText()).isEqualTo("Idempotency-Key");
        }
        assertNothingWasWritten();
        answered(open("2026-11-03", "TENANT_ADMINISTRATOR", "k".repeat(128), null), 200);
    }

    @Test
    void eachInvalidFieldHasItsOwnDetail() throws Exception {
        seeded(TODAY, 1_000);
        for (String base : new String[] {"-1", "1.5", "\"abc\"", "null", "true", "99999999999999999999"}) {
            for (MockHttpServletRequestBuilder request : List.of(
                    open("2026-11-03", "TENANT_ADMINISTRATOR", KEY, "{\"openingBaseCents\":" + base + "}"),
                    adjust("2026-11-03", "TENANT_ADMINISTRATOR", "{\"openingBaseCents\":" + base + "}"))) {
                JsonNode refused = answered(request, 400);
                assertThat(refused.path("error").asText()).as(base).isEqualTo("VALIDATION_ERROR");
                assertThat(refused.path("details")).as(base).hasSize(1);
                assertThat(refused.path("details").get(0).path("field").asText()).as(base).isEqualTo("openingBaseCents");
            }
        }
        for (String date : new String[] {"2026-02-30", "tomorrow", "2026-11-04"}) {
            for (MockHttpServletRequestBuilder request : List.of(
                    open(date, "TENANT_ADMINISTRATOR", KEY, null), adjust(date, "TENANT_ADMINISTRATOR", "{\"openingBaseCents\":5}"))) {
                JsonNode refused = answered(request, 400);
                assertThat(refused.path("details").get(0).path("field").asText()).as(date).isEqualTo("businessDate");
            }
        }
        for (MockHttpServletRequestBuilder request : List.of(adjust("2026-11-03", "TENANT_ADMINISTRATOR", null),
                adjust("2026-11-03", "TENANT_ADMINISTRATOR", "{}"))) {
            assertThat(answered(request, 400).path("details").get(0).path("field").asText()).isEqualTo("openingBaseCents");
        }
        assertThat(answered(adjust("2026-11-03", "TENANT_ADMINISTRATOR", "{\"openingBaseCents\":"), 400)
                .path("details").get(0).path("field").asText()).isEqualTo("body");
        assertThat(answered(adjust("2026-11-03", "TENANT_ADMINISTRATOR", "[1]"), 400)
                .path("details").get(0).path("field").asText()).isEqualTo("body");
        assertThat(sessions.find(tenant, TODAY).orElseThrow().opening().base()).isEqualTo(Cents.of(1_000));
    }

    @Test
    void theAdministratorAdjustsTheBaseAndTheDayIsRecomputed() throws Exception {
        seeded(TODAY, 30_000_000);
        movements.seedRecorded(tenant, new RecordedMovement(UUID.randomUUID().toString(), TODAY,
                new CashMovement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, Cents.amount(10_000_000)),
                "Gasto", false, TestKeys.NOW, TestKeys.ACCOUNT, null));

        JsonNode day = answered(adjust("2026-11-03", "TENANT_ADMINISTRATOR", "{\"openingBaseCents\":35000000}"), 200);

        assertThat(day.path("openingBaseCents").asLong()).isEqualTo(35_000_000);
        assertThat(day.path("totalCents").asLong()).isEqualTo(25_000_000);
        assertThat(day.path("expectedCashCents").asLong()).isEqualTo(25_000_000);
        assertThat(answered(adjust("2026-11-03", "TENANT_ADMINISTRATOR", "{\"openingBaseCents\":35000000}"), 200)).isEqualTo(day);
        assertThat(eventsOfTheTenant()).as("the base publishes no event").isZero();
    }

    @Test
    void aCollaboratorEvenWithThePermissionCannotAdjustTheBase() throws Exception {
        seeded(TODAY, 30_000_000);

        JsonNode refused = answered(adjust("2026-11-03", "OPERATIONAL_COLLABORATOR", "{\"openingBaseCents\":1}",
                TestKeys.MANAGE_CASH), 403);

        assertThat(refused.path("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(sessions.find(tenant, TODAY).orElseThrow().opening().base()).isEqualTo(Cents.of(30_000_000));
    }

    @Test
    void theStateRulesOfTheBaseAnswer422() throws Exception {
        String body = "{\"openingBaseCents\":5}";
        assertThat(answered(adjust("2026-11-03", "TENANT_ADMINISTRATOR", body), 422).path("error").asText())
                .isEqualTo("NO_OPEN_CASH");

        seeded(YESTERDAY, 1_000);
        assertThat(answered(adjust("2026-11-02", "TENANT_ADMINISTRATOR", body), 422).path("error").asText())
                .isEqualTo("PENDING_CLOSURE");

        sessions.seed(seeded(TODAY, 1_000).close(ClosingMode.MANUAL));
        assertThat(answered(adjust("2026-11-03", "TENANT_ADMINISTRATOR", body), 422).path("error").asText())
                .isEqualTo("ALREADY_CLOSED");
    }
}
