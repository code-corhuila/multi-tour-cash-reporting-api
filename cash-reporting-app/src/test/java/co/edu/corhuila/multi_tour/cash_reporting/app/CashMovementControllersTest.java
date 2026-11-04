package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C level, the movements of HU-CSH-001: every response is validated against openapi/openapi.yaml. In-memory
 * repositories, fixed clock (2026-11-03 07:00 in Bogota), one tenant per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class CashMovementControllersTest extends WriteControllersSupport {

    private static final String PATH = "/cash-days/2026-11-03/movements";

    private static String body(String type, String medium, String concept, String amount) {
        return "{\"movementType\":" + type + ",\"paymentMedium\":" + medium + ",\"concept\":" + concept
                + ",\"amountCents\":" + amount + "}";
    }

    private static final String EXPENSE = body("\"OPERATIONAL_EXPENSE\"", "\"CASH\"", "\"Compra operativa\"", "25000");

    private List<String> eventTypes() {
        return outbox.events().stream().filter(event -> event.tenantId().equals(tenant))
                .map(event -> event.eventType()).toList();
    }

    @Test
    void anExpenseInCashIsRegisteredWith201AndItsLocation() throws Exception {
        seedOpen(TODAY, 100_000);

        MvcResult result = mvc.perform(write(PATH, KEY, EXPENSE)).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/cash-reporting/cash-days/2026-11-03/movements"))
                .andReturn();
        ContractCheck.responseMatches(result);
        JsonNode movement = JSON.readTree(result.getResponse().getContentAsString());

        assertThat(movement.path("movementType").asText()).isEqualTo("OPERATIONAL_EXPENSE");
        assertThat(movement.path("paymentMedium").asText()).isEqualTo("CASH");
        assertThat(movement.path("concept").asText()).isEqualTo("Compra operativa");
        assertThat(movement.path("amountCents").asLong()).isEqualTo(25_000);
        assertThat(movement.path("afterClosing").asBoolean()).isFalse();
        assertThat(movement.path("registeredBy").path("userId").asText()).isEqualTo(TestKeys.ACCOUNT);
        assertThat(movements.findById(tenant, movement.path("cashMovementId").asText())).isPresent();
        assertThat(keys.find(tenant, KEY)).isPresent();
        assertThat(eventTypes()).containsExactly("OperationalExpenseRecorded");
    }

    @Test
    void aCollaboratorWithThePermissionRegistersAnIncome() throws Exception {
        seedOpen(TODAY, 0);

        JsonNode movement = answered(write(PATH, token("OPERATIONAL_COLLABORATOR", TestKeys.MANAGE_CASH), KEY,
                body("\"INCOME\"", "\"CASH\"", "\"Venta de mostrador\"", "70000")), 201);

        assertThat(movement.path("movementType").asText()).isEqualTo("INCOME");
        assertThat(eventTypes()).containsExactly("CashIncomeRecorded");
    }

    @Test
    void repeatingTheKeyAnswers200WithTheSameMovementAndWritesNothingTwice() throws Exception {
        seedOpen(TODAY, 100_000);
        JsonNode first = answered(write(PATH, KEY, EXPENSE), 201);
        int rows = movements.writes();

        JsonNode again = answered(write(PATH, KEY, body("\"INCOME\"", "\"TRANSFER\"", "\"Otro\"", "1")), 200);

        assertThat(again).isEqualTo(first);
        assertThat(movements.writes()).isEqualTo(rows);
        assertThat(eventsOfTheTenant()).isEqualTo(1);
    }

    @Test
    void theSameKeyOfAnotherTenantIsAnotherMovement() throws Exception {
        seedOpen(TODAY, 100_000);
        JsonNode mine = answered(write(PATH, KEY, EXPENSE), 201);
        String other = UUID.randomUUID().toString();
        sessions.seed(CashSession.openNew(other, TODAY, BOGOTA,
                new CashSession.Opening(Cents.of(1), TestKeys.NOW, TestKeys.ACCOUNT), TODAY));
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("tenantId", other);
        String otherToken = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");

        JsonNode theirs = answered(write(PATH, otherToken, KEY, EXPENSE), 201);

        assertThat(theirs.path("cashMovementId").asText()).isNotEqualTo(mine.path("cashMovementId").asText());
        assertThat(movements.findById(other, theirs.path("cashMovementId").asText())).isPresent();
        assertThat(movements.findById(tenant, theirs.path("cashMovementId").asText())).isEmpty();
    }

    @Test
    void cashWithoutOpeningAnswers422NoOpenCash() throws Exception {
        assertThat(errorOf(answered(write(PATH, KEY, EXPENSE), 422))).isEqualTo("NO_OPEN_CASH");
        assertNothingWasWritten(KEY);

        sessions.seed(CashSession.automatic(tenant, TODAY, BOGOTA));
        assertThat(errorOf(answered(write(PATH, KEY, EXPENSE), 422))).isEqualTo("NO_OPEN_CASH");
        assertThat(keys.find(tenant, KEY)).isEmpty();
        assertThat(eventsOfTheTenant()).isZero();
    }

    @Test
    void aTransferOnADayWithoutCashCreatesTheCashAutomatically() throws Exception {
        JsonNode movement = answered(write(PATH, KEY, body("\"INCOME\"", "\"TRANSFER\"", "\"Pago PSE\"", "90000")), 201);

        assertThat(movement.path("paymentMedium").asText()).isEqualTo("TRANSFER");
        CashSession created = sessions.find(tenant, TODAY).orElseThrow();
        assertThat(created.origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(created.hasOperationalOpening()).isFalse();
        assertThat(eventTypes()).containsExactly("CashSessionAutoCreated", "CashIncomeRecorded");
    }

    @Test
    void theClosedDayRefusesCashButStoresATransferAfterTheClosing() throws Exception {
        seedOpen(TODAY, 100_000);
        sessions.seed(sessions.find(tenant, TODAY).orElseThrow().close(ClosingMode.MANUAL));

        assertThat(errorOf(answered(write(PATH, KEY, EXPENSE), 422))).isEqualTo("ALREADY_CLOSED");

        JsonNode late = answered(write(PATH, "late-transfer-1", body("\"INCOME\"", "\"TRANSFER\"", "\"Tarde\"", "5")), 201);
        assertThat(late.path("afterClosing").asBoolean()).isTrue();
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status().name()).isEqualTo("CLOSED");
    }

    @Test
    void aPendingDayAndAnotherDayAnswer422() throws Exception {
        seedOpen(YESTERDAY, 1_000);

        assertThat(errorOf(answered(write("/cash-days/2026-11-02/movements", KEY, EXPENSE), 422)))
                .isEqualTo("PENDING_CLOSURE");
        assertThat(errorOf(answered(write("/cash-days/2026-10-20/movements", "other-day-key", EXPENSE), 422)))
                .isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(movements.findByDate(tenant, TODAY)).isEmpty();
    }

    @Test
    void aClientCannotRegisterARefundMovement() throws Exception {
        seedOpen(TODAY, 100_000);
        for (String refund : List.of(body("\"REFUND\"", "\"CASH\"", "\"Devolucion\"", "100"),
                body("\"REFUND\"", "\"TRANSFER\"", "\"Devolucion\"", "100"))) {
            JsonNode refused = answered(write(PATH, KEY, refund), 400);
            assertThat(errorOf(refused)).isEqualTo("VALIDATION_ERROR");
            assertThat(firstField(refused)).isEqualTo("movementType");
        }
        JsonNode withReservation = answered(write(PATH, KEY, EXPENSE.replace("}",
                ",\"reservationId\":\"" + UUID.randomUUID() + "\"}")), 400);
        assertThat(firstField(withReservation)).isEqualTo("reservationId");
        assertThat(movements.findByDate(tenant, TODAY)).isEmpty();
        assertThat(keys.find(tenant, KEY)).isEmpty();
        assertThat(eventsOfTheTenant()).isZero();
    }

    @Test
    void aMovementWithAStatusFieldIsRejected() throws Exception {
        seedOpen(TODAY, 100_000);
        for (String field : List.of("status", "tenantId", "afterClosing")) {
            String value = field.equals("afterClosing") ? "true" : "\"PENDING\"";
            JsonNode refused = answered(write(PATH, KEY, EXPENSE.replace("}", ",\"" + field + "\":" + value + "}")), 400);
            assertThat(errorOf(refused)).as(field).isEqualTo("VALIDATION_ERROR");
            assertThat(firstField(refused)).as(field).isEqualTo(field);
        }
        assertThat(movements.findByDate(tenant, TODAY)).isEmpty();
    }

    @Test
    void eachInvalidFieldHasItsOwnDetail() throws Exception {
        seedOpen(TODAY, 100_000);
        JsonNode all = answered(write(PATH, KEY, "{\"movementType\":\"BONUS\",\"paymentMedium\":\"CARD\","
                + "\"concept\":\"  \",\"amountCents\":0}"), 400);
        assertThat(StreamSupport.stream(all.path("details").spliterator(), false).map(d -> d.path("field").asText()))
                .containsExactlyInAnyOrder("movementType", "paymentMedium", "concept", "amountCents");

        assertThat(answered(write(PATH, KEY, "{}"), 400).path("details")).hasSize(4);

        for (String amount : List.of("-5", "1.5", "\"10\"", "null", "true", "99999999999999999999")) {
            JsonNode refused = answered(write(PATH, KEY, body("\"INCOME\"", "\"CASH\"", "\"x\"", amount)), 400);
            assertThat(refused.path("details")).as(amount).hasSize(1);
            assertThat(firstField(refused)).as(amount).isEqualTo("amountCents");
        }
        assertThat(firstField(answered(write(PATH, KEY,
                body("\"INCOME\"", "\"CASH\"", "\"" + "c".repeat(201) + "\"", "5")), 400))).isEqualTo("concept");
        assertThat(firstField(answered(write(PATH, KEY, body("1", "\"CASH\"", "\"x\"", "5")), 400)))
                .isEqualTo("movementType");
        assertThat(firstField(answered(write(PATH, KEY, null), 400))).isEqualTo("body");
        assertThat(firstField(answered(write(PATH, KEY, "[1]"), 400))).isEqualTo("body");
        assertThat(firstField(answered(write(PATH, KEY, "{\"movementType\":"), 400))).isEqualTo("body");
        assertThat(firstField(answered(write("/cash-days/2026-02-30/movements", KEY, EXPENSE), 400)))
                .isEqualTo("businessDate");
        assertThat(firstField(answered(write("/cash-days/2026-11-04/movements", KEY, EXPENSE), 400)))
                .isEqualTo("businessDate");
        assertThat(movements.findByDate(tenant, TODAY)).isEmpty();
    }

    @Test
    void aMissingOrMalformedKeyIsRejected() throws Exception {
        seedOpen(TODAY, 100_000);
        for (String key : new String[] {null, "", "        ", "short-7", "k".repeat(129)}) {
            JsonNode refused = answered(write(PATH, key, EXPENSE), 400);
            assertThat(errorOf(refused)).as("key [" + key + "]").isEqualTo("VALIDATION_ERROR");
            assertThat(firstField(refused)).isEqualTo("Idempotency-Key");
        }
        assertThat(movements.findByDate(tenant, TODAY)).isEmpty();
        answered(write(PATH, "k".repeat(128), EXPENSE), 201);
    }

    @Test
    void thePermissionIsCheckedBeforeTheShape() throws Exception {
        for (String role : List.of("FINAL_CUSTOMER", "MANAGER", "ANALYST", "OPERATIONAL_COLLABORATOR")) {
            JsonNode refused = answered(write(PATH, token(role), null, "{\"movementType\":\"REFUND\"}"), 403);
            assertThat(errorOf(refused)).as(role).isEqualTo("FORBIDDEN");
        }
        assertThat(errorOf(answered(write("/cash-days/not-a-date/movements", token("MANAGER"), "bad", "x"), 403)))
                .isEqualTo("FORBIDDEN");
        assertNothingWasWritten(KEY);
    }
}
