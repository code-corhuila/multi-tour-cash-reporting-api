package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
 * C level, read side of HU-CSH-001: every response is validated against openapi/openapi.yaml. The repositories are the
 * in-memory fakes; the fixed clock says it is 2026-11-03 07:00 in Bogota. Each test works with a tenant of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class CashReadControllersTest {

    private static final String BASE = "/api/v1/cash-reporting";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final BusinessDate TODAY = BusinessDate.of(LocalDate.of(2026, 11, 3));
    private static final BusinessDate YESTERDAY = BusinessDate.of(LocalDate.of(2026, 11, 2));
    private static final BusinessDate TWO_DAYS_AGO = BusinessDate.of(LocalDate.of(2026, 11, 1));
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private InMemoryCashSessionRepository sessions;
    @Autowired
    private InMemoryCashMovementRepository movements;
    @Autowired
    private InMemoryCashClosureRepository closures;
    @Autowired
    private InMemoryCashReopeningRepository reopenings;

    private final String tenant = UUID.randomUUID().toString();

    private String token(String role, String... permissions) {
        Map<String, Object> claims = TestKeys.claims(role, permissions);
        claims.put("tenantId", tenant);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MockHttpServletRequestBuilder read(String path, String role) {
        return get(BASE + path).header("Authorization", "Bearer " + token(role));
    }

    private JsonNode answered(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private CashSession opened(BusinessDate date, long baseCents) {
        return CashSession.openNew(tenant, date, BOGOTA,
                new CashSession.Opening(Cents.of(baseCents), TestKeys.NOW, TestKeys.ACCOUNT), date);
    }

    private void movement(BusinessDate date, MovementType type, PaymentMedium medium, long cents, int minute) {
        boolean refund = type == MovementType.REFUND;
        movements.seedRecorded(tenant, new RecordedMovement(UUID.randomUUID().toString(), date,
                new CashMovement(type, medium, Cents.amount(cents)), "Concepto " + minute, false,
                TestKeys.NOW.plusSeconds(60L * minute), TestKeys.ACCOUNT,
                refund ? new RefundDetail(UUID.randomUUID().toString()) : null));
    }

    @Test
    void theCurrentDayShowsTheFiveMetricsAsTheServiceComputed() throws Exception {
        sessions.seed(opened(TODAY, 30_000_000));
        movement(TODAY, MovementType.INCOME, PaymentMedium.CASH, 29_000_000, 1);
        movement(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 100_000_000, 2);
        movement(TODAY, MovementType.OPERATIONAL_PAYMENT, PaymentMedium.TRANSFER, 4_000_000, 3);
        movement(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 25_000_000, 4);
        movement(TODAY, MovementType.REFUND, PaymentMedium.CASH, 15_000_000, 5);

        JsonNode day = answered(read("/cash-days/current", "MANAGER"), 200);

        assertThat(day.path("businessDate").asText()).isEqualTo("2026-11-03");
        assertThat(day.path("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(day.path("cashSessionId").asText()).matches(UUID_PATTERN);
        assertThat(day.path("status").asText()).isEqualTo("OPEN");
        assertThat(day.path("origin").asText()).isEqualTo("OPERATIONAL_OPENING");
        assertThat(day.path("hasOperationalOpening").asBoolean()).isTrue();
        assertThat(day.path("pendingClosure").asBoolean()).isFalse();
        assertThat(day.path("openingBaseCents").asLong()).isEqualTo(30_000_000);
        assertThat(day.path("incomeCents").asLong()).isEqualTo(129_000_000);
        assertThat(day.path("operationalPaymentsCents").asLong()).isEqualTo(4_000_000);
        assertThat(day.path("expensesCents").asLong()).isEqualTo(25_000_000);
        assertThat(day.path("refundsCents").asLong()).isEqualTo(15_000_000);
        // total = base + income - payments - expenses - refunds; expected cash = base + cash entries - cash exits.
        assertThat(day.path("totalCents").asLong()).isEqualTo(115_000_000);
        assertThat(day.path("expectedCashCents").asLong()).isEqualTo(19_000_000);
        assertThat(day.path("netTransfersCents").asLong()).isEqualTo(96_000_000);
        assertThat(day.path("openedBy").path("userId").asText()).isEqualTo(TestKeys.ACCOUNT);
        assertThat(day.path("openedBy").path("roleLabel").asText()).isNotBlank();
        // A nullable field declared with allOf is absent, not null: the validator rejects a null there (the closedAt kind may be null).
        assertThat(day.path("closedAt").isNull()).isTrue();
        assertThat(day.path("closedBy").isMissingNode()).isTrue();
        assertThat(day.path("closingMode").isNull()).isTrue();
        assertThat(answered(read("/cash-days/2026-11-03", "ACCOUNTANT"), 200)).isEqualTo(day);
    }

    @Test
    void aDayWithoutCashAnswers200NoOpening() throws Exception {
        for (String path : List.of("/cash-days/current", "/cash-days/2026-10-01")) {
            JsonNode day = answered(read(path, "OPERATIONAL_COLLABORATOR"), 200);
            assertThat(day.path("status").asText()).isEqualTo("NO_OPENING");
            assertThat(day.path("cashSessionId").isNull()).isTrue();
            assertThat(day.path("origin").isNull()).isTrue();
            assertThat(day.path("hasOperationalOpening").asBoolean()).isFalse();
            assertThat(day.path("pendingClosure").asBoolean()).isFalse();
            assertThat(day.path("openingBaseCents").isMissingNode()).isTrue();
            assertThat(day.path("expectedCashCents").isMissingNode()).isTrue();
            for (String field : List.of("incomeCents", "operationalPaymentsCents", "expensesCents", "refundsCents",
                    "totalCents", "netTransfersCents")) {
                assertThat(day.path(field).asLong()).as(field).isZero();
            }
        }
        assertThat(answered(read("/cash-days/2026-10-01/movements", "ANALYST"), 200).path("data")).isEmpty();
    }

    @Test
    void aPastDayOpenedAndNeverClosedIsPendingAndAFutureDateIsRefused() throws Exception {
        sessions.seed(opened(YESTERDAY, 1_000_000));
        assertThat(answered(read("/cash-days/2026-11-02", "TENANT_ADMINISTRATOR"), 200).path("pendingClosure")
                .asBoolean()).isTrue();
        for (String path : List.of("/cash-days/2026-11-04", "/cash-days/2026-11-04/movements")) {
            JsonNode refused = answered(read(path, "TENANT_ADMINISTRATOR"), 400);
            assertThat(refused.path("error").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(refused.path("details").get(0).path("field").asText()).isEqualTo("businessDate");
        }
    }

    @Test
    void aListWithLimitOutOfRangeAnswers400() throws Exception {
        for (String list : List.of("/cash-days", "/cash-days/2026-11-03/movements", "/cash-closures")) {
            for (String query : List.of("limit=0", "limit=101", "limit=-5", "limit=abc", "page=0")) {
                JsonNode refused = answered(read(list + "?" + query, "TENANT_ADMINISTRATOR"), 400);
                assertThat(refused.path("error").asText()).as(list + "?" + query).isEqualTo("VALIDATION_ERROR");
                assertThat(refused.path("details").get(0).path("field").asText()).isEqualTo(query.split("=")[0]);
            }
            answered(read(list + "?limit=1", "TENANT_ADMINISTRATOR"), 200);
            answered(read(list + "?limit=100", "TENANT_ADMINISTRATOR"), 200);
        }
    }

    @Test
    void everyListIsDataAndMeta() throws Exception {
        sessions.seed(opened(TODAY, 1_000_000));
        sessions.seed(opened(YESTERDAY, 2_000_000));
        sessions.seed(opened(TWO_DAYS_AGO, 3_000_000).close(ClosingMode.MANUAL));
        for (int minute = 1; minute <= 3; minute++) {
            movement(TODAY, MovementType.INCOME, PaymentMedium.CASH, 1_000L * minute, minute);
        }
        closure(TWO_DAYS_AGO, ClosingMode.MANUAL, 22);
        closure(YESTERDAY, ClosingMode.AUTOMATIC, 23);

        JsonNode days = answered(read("/cash-days?limit=2", "MANAGER"), 200);
        assertThat(days.path("data")).hasSize(2);
        assertThat(days.path("data").get(0).path("businessDate").asText()).isEqualTo("2026-11-03");
        assertThat(days.path("data").get(1).path("businessDate").asText()).isEqualTo("2026-11-02");
        assertThat(days.path("meta")).isEqualTo(JSON.readTree("{\"page\":1,\"limit\":2,\"total\":3,\"totalPages\":2}"));

        JsonNode listed = answered(read("/cash-days/2026-11-03/movements?page=2&limit=2", "ACCOUNTANT"), 200);
        assertThat(listed.path("data")).hasSize(1);
        assertThat(listed.path("meta")).isEqualTo(JSON.readTree("{\"page\":2,\"limit\":2,\"total\":3,\"totalPages\":2}"));

        JsonNode history = answered(read("/cash-closures", "ANALYST"), 200);
        assertThat(history.path("data")).hasSize(2);
        assertThat(history.path("meta")).isEqualTo(JSON.readTree("{\"page\":1,\"limit\":20,\"total\":2,\"totalPages\":1}"));
    }

    @Test
    void theListOfDaysFiltersThePendingClosureAndNothingOfAnotherTenant() throws Exception {
        sessions.seed(opened(TODAY, 1_000_000));
        sessions.seed(opened(YESTERDAY, 2_000_000));
        sessions.seed(CashSession.openNew("another-tenant", TWO_DAYS_AGO, BOGOTA,
                new CashSession.Opening(Cents.of(5), TestKeys.NOW, "someone"), TWO_DAYS_AGO));

        JsonNode pending = answered(read("/cash-days?pendingClosure=true", "TENANT_ADMINISTRATOR"), 200);
        assertThat(pending.path("data")).hasSize(1);
        assertThat(pending.path("data").get(0).path("businessDate").asText()).isEqualTo("2026-11-02");
        assertThat(pending.path("data").get(0).path("pendingClosure").asBoolean()).isTrue();
        assertThat(answered(read("/cash-days", "TENANT_ADMINISTRATOR"), 200).path("meta").path("total").asInt())
                .isEqualTo(2);
    }

    @Test
    void theMovementsOfADayCarryTheirReservationOnARefundAndComeNewestFirst() throws Exception {
        sessions.seed(opened(TODAY, 1_000_000));
        movement(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 5_000, 1);
        movement(TODAY, MovementType.REFUND, PaymentMedium.CASH, 2_000, 2);

        JsonNode data = answered(read("/cash-days/2026-11-03/movements", "OPERATIONAL_COLLABORATOR"), 200)
                .path("data");

        assertThat(data.get(0).path("movementType").asText()).isEqualTo("REFUND");
        assertThat(data.get(0).path("reservationId").asText()).matches(UUID_PATTERN);
        assertThat(data.get(0).path("amountCents").asLong()).isEqualTo(2_000);
        assertThat(data.get(0).path("registeredBy").path("userId").asText()).isEqualTo(TestKeys.ACCOUNT);
        assertThat(data.get(1).path("movementType").asText()).isEqualTo("INCOME");
        assertThat(data.get(1).path("paymentMedium").asText()).isEqualTo("TRANSFER");
        assertThat(data.get(1).path("reservationId").isNull()).isTrue();
        assertThat(data.get(1).path("businessDate").asText()).isEqualTo("2026-11-03");
    }

    @Test
    void theClosureHistoryShowsEachClosingWithItsBadgeAndItsReopenings() throws Exception {
        CashClosure manual = closure(TWO_DAYS_AGO, ClosingMode.MANUAL, 22);
        closure(YESTERDAY, ClosingMode.AUTOMATIC, 23);
        reopenings.insert(tenant, new CashReopening(UUID.randomUUID().toString(), tenant, TWO_DAYS_AGO, manual.id(),
                "Error de conteo", TestKeys.ACCOUNT, TestKeys.NOW.minusSeconds(60)));

        JsonNode data = answered(read("/cash-closures", "MANAGER"), 200).path("data");

        JsonNode automatic = data.get(0);
        assertThat(automatic.path("businessDate").asText()).isEqualTo("2026-11-02");
        assertThat(automatic.path("badge").asText()).isEqualTo("AUTOMATIC_CLOSING");
        assertThat(automatic.path("closingMode").asText()).isEqualTo("AUTOMATIC");
        assertThat(automatic.path("closedBy").isMissingNode()).isTrue();
        assertThat(automatic.path("baseCents").asLong()).isEqualTo(10_000_000);
        assertThat(automatic.path("expectedCashCents").asLong()).isEqualTo(15_000_000);
        assertThat(automatic.path("hasOperationalOpening").asBoolean()).isTrue();
        assertThat(automatic.path("origin").asText()).isEqualTo("OPERATIONAL_OPENING");
        JsonNode reopened = data.get(1);
        assertThat(reopened.path("badge").asText()).isEqualTo("REOPENED");
        assertThat(reopened.path("closingMode").asText()).isEqualTo("FORMAL");
        assertThat(reopened.path("closedBy").path("userId").asText()).isEqualTo(TestKeys.ACCOUNT);
        assertThat(reopened.path("baseCents").asLong()).isEqualTo(10_000_000);
        assertThat(reopened.path("totalCents").asLong()).isEqualTo(15_000_000);
        assertThat(reopened.path("entriesCashCents").asLong()).isEqualTo(5_000_000);
        assertThat(reopened.path("expectedCashCents").asLong()).isEqualTo(15_000_000);
        assertThat(reopened.path("reopenings")).hasSize(1);
        assertThat(reopened.path("reopenings").get(0).path("reason").asText()).isEqualTo("Error de conteo");
        assertThat(reopened.path("corrections")).isEmpty();
    }

    /** A day opened with 10,000,000 and one cash income of 5,000,000, closed by hand or by the service. */
    private CashClosure closure(BusinessDate date, ClosingMode mode, int hour) {
        var view = DayTotals.compute(Cents.of(10_000_000),
                List.of(new CashMovement(MovementType.INCOME, PaymentMedium.CASH, Cents.of(5_000_000))));
        return closures.insert(tenant, CashClosure.snapshot(UUID.randomUUID().toString(), opened(date, 10_000_000), view,
                mode, mode == ClosingMode.AUTOMATIC ? "system" : TestKeys.ACCOUNT,
                Instant.parse("2026-11-0" + (date.equals(YESTERDAY) ? 2 : 1) + "T" + hour + ":00:00Z"), false));
    }

    // The contract declares baseCents required and nullable through allOf, which the validator cannot satisfy for a
    // cash that never had an operational opening (null and absent are both refused): the body is checked here without it.
    @Test
    void aClosureOfACashWithoutOpeningHasNoBaseAndNoExpectedCash() throws Exception {
        var view = DayTotals.compute(null, List.of());
        closures.insert(tenant, CashClosure.snapshot(UUID.randomUUID().toString(),
                CashSession.automatic(tenant, YESTERDAY, BOGOTA), view, ClosingMode.AUTOMATIC, "system", TestKeys.NOW, false));

        MvcResult result = mvc.perform(read("/cash-closures", "MANAGER")).andExpect(status().isOk()).andReturn();

        JsonNode entry = JSON.readTree(result.getResponse().getContentAsString()).path("data").get(0);
        assertThat(entry.path("baseCents").isNull()).isTrue();
        assertThat(entry.path("expectedCashCents").isMissingNode()).isTrue();
        assertThat(entry.path("origin").asText()).isEqualTo("AUTOMATIC_CREATION");
        assertThat(entry.path("hasOperationalOpening").asBoolean()).isFalse();
        assertThat(entry.path("totalCents").asLong()).isZero();
    }
    @Test
    void aPlatformAdministratorAnswers403() throws Exception {
        Map<String, Object> claims = TestKeys.claims("PLATFORM_ADMINISTRATOR");
        claims.put("tenantId", null);
        String withoutTenant = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        for (String path : List.of("/cash-days", "/cash-days/current", "/cash-days/2026-11-03",
                "/cash-days/2026-11-03/movements", "/cash-closures")) {
            MvcResult result = mvc.perform(get(BASE + path).header("Authorization", "Bearer " + withoutTenant))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN")).andReturn();
            ContractCheck.responseMatches(result);
        }
    }

    @Test
    void everyOperationAnswers403ToItsForbiddenRoles() throws Exception {
        // The read half: nobody outside the five tenant roles reads; a tenant platform administrator, a final customer,
        // an unknown role and a token with no role all fail closed. The write half (opening and base) follows the reads;
        // the rest of the writes join it with their controllers.
        for (String role : List.of("FINAL_CUSTOMER", "PLATFORM_ADMINISTRATOR", "SUPERUSER", "tenant_administrator")) {
            for (String path : List.of("/cash-days", "/cash-days/current", "/cash-days/2026-11-03",
                    "/cash-days/2026-11-03/movements", "/cash-closures")) {
                JsonNode refused = answered(read(path, role), 403);
                assertThat(refused.path("error").asText()).as(role + " " + path).isEqualTo("FORBIDDEN");
            }
        }
        Map<String, Object> claims = TestKeys.claims("MANAGER");
        claims.put("tenantId", tenant);
        claims.put("roles", List.of());
        String noRole = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        answered(get(BASE + "/cash-days/current").header("Authorization", "Bearer " + noRole), 403);

        // The write half. Valid keys and bodies, so the refusal is the role and not the shape. Opening: the optional
        // roles and a Collaborator without the permission; the base: also a Collaborator WITH it. Nothing is written.
        sessions.seed(opened(TODAY, 1_000_000));
        String openingBody = "{\"openingBaseCents\":5}";
        for (String[] caller : new String[][] {{"FINAL_CUSTOMER"}, {"PLATFORM_ADMINISTRATOR"}, {"SUPERUSER"},
                {"tenant_administrator"}, {"MANAGER"}, {"ACCOUNTANT"}, {"ANALYST"}, {"OPERATIONAL_COLLABORATOR"}}) {
            JsonNode refused = answered(post(BASE + "/cash-days/2026-11-03/open")
                    .header("Authorization", "Bearer " + token(caller[0])).header("Idempotency-Key", "forbidden-key-1")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"), 403);
            assertThat(refused.path("error").asText()).as("open " + caller[0]).isEqualTo("FORBIDDEN");
        }
        for (String[] caller : new String[][] {{"FINAL_CUSTOMER"}, {"PLATFORM_ADMINISTRATOR"}, {"SUPERUSER"},
                {"tenant_administrator"}, {"MANAGER"}, {"ACCOUNTANT"}, {"ANALYST"}, {"OPERATIONAL_COLLABORATOR"},
                {"OPERATIONAL_COLLABORATOR", TestKeys.MANAGE_CASH}}) {
            JsonNode refused = answered(post(BASE + "/cash-days/2026-11-03/adjust-base")
                    .header("Authorization", "Bearer " + token(caller[0], java.util.Arrays.copyOfRange(caller, 1, caller.length)))
                    .contentType(MediaType.APPLICATION_JSON).content(openingBody), 403);
            assertThat(refused.path("error").asText()).as("adjust " + String.join("+", caller)).isEqualTo("FORBIDDEN");
        }
        assertThat(sessions.find(tenant, TODAY).orElseThrow().opening().base()).isEqualTo(Cents.of(1_000_000));

        // The movements, the closing and the reopening: the same five refused roles plus a Collaborator without the
        // permission, with a valid key and body. The day stays open and unchanged, and nothing is stored.
        String movementBody = "{\"movementType\":\"OPERATIONAL_EXPENSE\",\"paymentMedium\":\"CASH\",\"concept\":\"x\","
                + "\"amountCents\":5}";
        for (String role : List.of("FINAL_CUSTOMER", "PLATFORM_ADMINISTRATOR", "SUPERUSER", "tenant_administrator",
                "MANAGER", "ACCOUNTANT", "ANALYST", "OPERATIONAL_COLLABORATOR")) {
            for (String[] operation : new String[][] {{"/cash-days/2026-11-03/movements", movementBody},
                    {"/cash-days/2026-11-03/close", null}, {"/cash-days/2026-11-03/reopen", "{\"reason\":\"motivo\"}"}}) {
                MockHttpServletRequestBuilder request = post(BASE + operation[0])
                        .header("Authorization", "Bearer " + token(role)).header("Idempotency-Key", "forbidden-key-2");
                JsonNode refused = answered(operation[1] == null ? request
                        : request.contentType(MediaType.APPLICATION_JSON).content(operation[1]), 403);
                assertThat(refused.path("error").asText()).as(operation[0] + " " + role).isEqualTo("FORBIDDEN");
            }
        }
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status().name()).isEqualTo("OPEN");
        assertThat(movements.findByDate(tenant, TODAY)).isEmpty();
    }

    @Test
    void anInvalidParameterNamesItsOwnField() throws Exception {
        Map<String, String> fieldOf = Map.of(
                "/cash-days?pendingClosure=maybe", "pendingClosure",
                "/cash-days/2026-02-30", "businessDate",
                "/cash-days/not-a-date/movements", "businessDate",
                "/cash-days?page=abc", "page");
        for (Map.Entry<String, String> entry : fieldOf.entrySet()) {
            JsonNode refused = answered(read(entry.getKey(), "TENANT_ADMINISTRATOR"), 400);
            assertThat(refused.path("error").asText()).as(entry.getKey()).isEqualTo("VALIDATION_ERROR");
            assertThat(refused.path("details").get(0).path("field").asText()).as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void unreadableJsonNamesTheFieldThatCannotBeRead() throws Exception {
        MockHttpServletRequestBuilder wrongType = post(BASE + "/_probe/echo")
                .header("Authorization", "Bearer " + token("TENANT_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"concept\":{\"nested\":1}}");
        mvc.perform(wrongType).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("concept"));
    }
}
