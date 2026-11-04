package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * C level, closing and reopening of HU-CSH-001: every response is validated against openapi/openapi.yaml. In-memory
 * repositories, fixed clock (2026-11-03 07:00 in Bogota), one tenant per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class CashCloseAndReopenControllersTest extends WriteControllersSupport {

    private static final String CLOSE = "/cash-days/2026-11-03/close";
    private static final String REOPEN = "/cash-days/2026-11-03/reopen";
    private static final String REASON = "{\"reason\":\"  Se omitio un pago operacional  \"}";

    private void register(String type, String medium, long cents, String key) throws Exception {
        answered(write("/cash-days/2026-11-03/movements", key, "{\"movementType\":\"" + type + "\",\"paymentMedium\":\""
                + medium + "\",\"concept\":\"x\",\"amountCents\":" + cents + "}"), 201);
    }

    private List<String> eventTypes() {
        return outbox.events().stream().filter(event -> event.tenantId().equals(tenant))
                .map(event -> event.eventType()).toList();
    }

    @Test
    void closingKeepsTheSnapshotOfTheDayAndAnswersTheClosedDay() throws Exception {
        seedOpen(TODAY, 100_000);
        register("OPERATIONAL_EXPENSE", "CASH", 25_000, "movement-key-1");
        register("INCOME", "TRANSFER", 40_000, "movement-key-2");

        JsonNode day = answered(write(CLOSE, KEY, null), 200);

        assertThat(day.path("status").asText()).isEqualTo("CLOSED");
        assertThat(day.path("closingMode").asText()).isEqualTo("FORMAL");
        assertThat(day.path("formalClosureOfPending").asBoolean()).isFalse();
        assertThat(day.path("closedBy").path("userId").asText()).isEqualTo(TestKeys.ACCOUNT);
        assertThat(day.path("expensesCents").asLong()).isEqualTo(25_000);
        assertThat(day.path("incomeCents").asLong()).isEqualTo(40_000);
        var closure = closures.findLatest(tenant, TODAY).orElseThrow();
        assertThat(closure.view().expensesCents().value()).isEqualTo(25_000);
        assertThat(closure.view().incomeCents().value()).isEqualTo(40_000);
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(keys.find(tenant, KEY)).isPresent();
        assertThat(eventTypes()).endsWith("CashSessionClosed");
    }

    @Test
    void closingAPastOpenDayIsTheFormalClosureOfThePendingOne() throws Exception {
        seedOpen(YESTERDAY, 50_000);

        JsonNode day = answered(write("/cash-days/2026-11-02/close", KEY, null), 200);

        assertThat(day.path("businessDate").asText()).isEqualTo("2026-11-02");
        assertThat(day.path("status").asText()).isEqualTo("CLOSED");
        assertThat(day.path("formalClosureOfPending").asBoolean()).isTrue();
        assertThat(sessions.find(tenant, TODAY)).isEmpty();
    }

    @Test
    void repeatingTheKeyAnswers200WithTheSameClosureAndANewKeyIsAlreadyClosed() throws Exception {
        seedOpen(TODAY, 100_000);
        JsonNode first = answered(write(CLOSE, KEY, null), 200);
        int rows = closures.writes();

        assertThat(answered(write(CLOSE, KEY, null), 200)).isEqualTo(first);
        assertThat(closures.writes()).isEqualTo(rows);
        assertThat(eventsOfTheTenant()).isEqualTo(1);
        assertThat(errorOf(answered(write(CLOSE, "another-key-01", null), 422))).isEqualTo("ALREADY_CLOSED");
    }

    @Test
    void theStateRulesOfTheClosingAnswer422() throws Exception {
        assertThat(errorOf(answered(write(CLOSE, KEY, null), 422))).isEqualTo("NO_OPEN_CASH");
        sessions.seed(CashSession.automatic(tenant, TODAY, BOGOTA));
        assertThat(errorOf(answered(write(CLOSE, KEY, null), 422))).isEqualTo("NO_OPEN_CASH");
        assertThat(closures.all(tenant, TODAY)).isEmpty();
        assertThat(keys.find(tenant, KEY)).isEmpty();
        assertThat(eventsOfTheTenant()).isZero();
    }

    @Test
    void aFutureOrInvalidDateAndAMalformedKeyAreRejectedWith400() throws Exception {
        seedOpen(TODAY, 100_000);
        assertThat(firstField(answered(write("/cash-days/2026-11-04/close", KEY, null), 400))).isEqualTo("businessDate");
        assertThat(firstField(answered(write("/cash-days/2026-02-30/close", KEY, null), 400))).isEqualTo("businessDate");
        for (String key : new String[] {null, "", "short-7", "k".repeat(129)}) {
            assertThat(firstField(answered(write(CLOSE, key, null), 400))).isEqualTo("Idempotency-Key");
        }
        assertThat(closures.all(tenant, TODAY)).isEmpty();
    }

    @Test
    void reopeningKeepsTheClosureAndASecondClosingMakesAnotherOne() throws Exception {
        seedOpen(TODAY, 100_000);
        answered(write(CLOSE, KEY, null), 200);

        JsonNode day = answered(write(REOPEN, "reopen-key-01", REASON), 200);

        assertThat(day.path("status").asText()).isEqualTo("OPEN");
        assertThat(day.path("closedAt").isNull()).isTrue();
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status()).isEqualTo(SessionStatus.OPEN);
        assertThat(closures.all(tenant, TODAY)).hasSize(1);
        assertThat(reopenings.rows()).filteredOn(row -> row.tenantId().equals(tenant)).singleElement().satisfies(row -> {
            assertThat(row.reason()).isEqualTo("Se omitio un pago operacional");
            assertThat(row.reopenedBy()).isEqualTo(TestKeys.ACCOUNT);
        });
        assertThat(eventTypes()).endsWith("CashSessionReopened");

        answered(write(CLOSE, "second-close-01", null), 200);
        assertThat(closures.all(tenant, TODAY)).hasSize(2);
    }

    @Test
    void repeatingTheReopeningKeyAnswers200AndWritesNothingTwice() throws Exception {
        seedOpen(TODAY, 100_000);
        answered(write(CLOSE, KEY, null), 200);
        JsonNode first = answered(write(REOPEN, "reopen-key-01", REASON), 200);
        long events = eventsOfTheTenant();

        assertThat(answered(write(REOPEN, "reopen-key-01", "{\"reason\":\"otro\"}"), 200)).isEqualTo(first);
        assertThat(eventsOfTheTenant()).isEqualTo(events);
        assertThat(reopenings.rows()).filteredOn(row -> row.tenantId().equals(tenant)).hasSize(1);
    }

    @Test
    void reopeningWithoutReasonAnswers400ReasonRequired() throws Exception {
        seedOpen(TODAY, 100_000);
        answered(write(CLOSE, KEY, null), 200);
        for (String body : new String[] {null, "{}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}", "{\"reason\":null}"}) {
            JsonNode refused = answered(write(REOPEN, "reopen-key-01", body), 400);
            assertThat(errorOf(refused)).as(String.valueOf(body)).isEqualTo("REASON_REQUIRED");
            assertThat(firstField(refused)).isEqualTo("reason");
        }
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(reopenings.rows()).filteredOn(row -> row.tenantId().equals(tenant)).isEmpty();
    }

    @Test
    void aMalformedReopeningRequestIsAValidationError() throws Exception {
        seedOpen(TODAY, 100_000);
        answered(write(CLOSE, KEY, null), 200);
        assertThat(firstField(answered(write(REOPEN, "reopen-key-01", "{\"reason\":5}"), 400))).isEqualTo("reason");
        assertThat(firstField(answered(write(REOPEN, "reopen-key-01", "{\"reason\":\"x\",\"tenantId\":\"" + tenant + "\"}"),
                400))).isEqualTo("tenantId");
        assertThat(firstField(answered(write(REOPEN, "reopen-key-01", "[1]"), 400))).isEqualTo("body");
        assertThat(firstField(answered(write(REOPEN, "reopen-key-01", "{\"reason\":"), 400))).isEqualTo("body");
        for (String key : new String[] {null, "", "short-7", "k".repeat(129)}) {
            JsonNode refused = answered(write(REOPEN, key, REASON), 400);
            assertThat(errorOf(refused)).isEqualTo("VALIDATION_ERROR");
            assertThat(firstField(refused)).isEqualTo("Idempotency-Key");
        }
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    void theStateRulesOfTheReopeningAnswer404And422() throws Exception {
        assertThat(errorOf(answered(write(REOPEN, "reopen-key-01", REASON), 404))).isEqualTo("NOT_FOUND");

        seedOpen(TODAY, 100_000);
        assertThat(errorOf(answered(write(REOPEN, "reopen-key-02", REASON), 422))).isEqualTo("ALREADY_OPEN");

        sessions.seed(sessions.find(tenant, TODAY).orElseThrow().close(ClosingMode.AUTOMATIC));
        assertThat(errorOf(answered(write(REOPEN, "reopen-key-03", REASON), 422))).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(reopenings.rows()).filteredOn(row -> row.tenantId().equals(tenant)).isEmpty();
    }

    @Test
    void thePermissionIsCheckedBeforeTheShape() throws Exception {
        for (String role : List.of("FINAL_CUSTOMER", "MANAGER", "ACCOUNTANT", "OPERATIONAL_COLLABORATOR")) {
            assertThat(errorOf(answered(write(CLOSE, token(role), null, null), 403))).as("close " + role).isEqualTo("FORBIDDEN");
            assertThat(errorOf(answered(write(REOPEN, token(role), null, "{}"), 403))).as("reopen " + role)
                    .isEqualTo("FORBIDDEN");
        }
        assertThat(errorOf(answered(write("/cash-days/not-a-date/close", token("MANAGER"), "bad", null), 403)))
                .isEqualTo("FORBIDDEN");
        // The same rule for the opening and the base, which PR 17 left answering 400 before 403.
        assertThat(errorOf(answered(write("/cash-days/2026-11-03/open", token("MANAGER"), null, "[1]"), 403)))
                .isEqualTo("FORBIDDEN");
        assertThat(errorOf(answered(write("/cash-days/not-a-date/adjust-base", token("MANAGER"), null, "[1]"), 403)))
                .isEqualTo("FORBIDDEN");
        assertNothingWasWritten(KEY);
    }
}
