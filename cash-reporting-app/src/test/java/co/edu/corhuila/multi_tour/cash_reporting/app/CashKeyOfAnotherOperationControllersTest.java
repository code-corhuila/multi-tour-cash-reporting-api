package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * C level, DP-A1-3 of HU-CSH-001: an Idempotency-Key whose stored resource belongs to another operation answers 400
 * VALIDATION_ERROR on Idempotency-Key, never 500, and writes nothing. Responses are validated against openapi.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class CashKeyOfAnotherOperationControllersTest extends WriteControllersSupport {

    private static final String DAY = "/cash-days/2026-11-03";
    private static final String MOVEMENT = "{\"movementType\":\"OPERATIONAL_EXPENSE\",\"paymentMedium\":\"CASH\","
            + "\"concept\":\"Compra\",\"amountCents\":10000}";
    private static final String REASON = "{\"reason\":\"Falto un pago\"}";

    private void assertRefusedOnTheKey(JsonNode refused) {
        assertThat(errorOf(refused)).isEqualTo("VALIDATION_ERROR");
        assertThat(firstField(refused)).isEqualTo("Idempotency-Key");
    }

    @Test
    void aKeyUsedByAnotherOperationAnswers400WhenOpening() throws Exception {
        answered(write(DAY + "/movements", KEY, MOVEMENT.replace("CASH", "TRANSFER")), 201);
        long events = eventsOfTheTenant();

        assertRefusedOnTheKey(answered(write(DAY + "/open", KEY, "{\"openingBaseCents\":5}"), 400));

        assertThat(eventsOfTheTenant()).isEqualTo(events);
        assertThat(sessions.find(tenant, TODAY).orElseThrow().hasOperationalOpening()).isFalse();
    }

    @Test
    void aKeyUsedByAnotherOperationAnswers400WhenRegisteringAMovement() throws Exception {
        answered(write(DAY + "/open", KEY, "{\"openingBaseCents\":5}"), 200);
        long events = eventsOfTheTenant();

        assertRefusedOnTheKey(answered(write(DAY + "/movements", KEY, MOVEMENT), 400));

        assertThat(eventsOfTheTenant()).isEqualTo(events);
        assertThat(movements.findByDate(tenant, TODAY)).isEmpty();
    }

    @Test
    void aKeyUsedByAnotherOperationAnswers400WhenClosing() throws Exception {
        seedOpen(TODAY, 100_000);
        answered(write(DAY + "/movements", KEY, MOVEMENT), 201);

        assertRefusedOnTheKey(answered(write(DAY + "/close", KEY, null), 400));

        assertThat(closures.all(tenant, TODAY)).isEmpty();
    }

    @Test
    void aKeyUsedByAnotherOperationAnswers400WhenReopening() throws Exception {
        seedOpen(TODAY, 100_000);
        answered(write(DAY + "/movements", KEY, MOVEMENT), 201);
        answered(write(DAY + "/close", "close-key-001", null), 200);
        long events = eventsOfTheTenant();

        assertRefusedOnTheKey(answered(write(DAY + "/reopen", KEY, REASON), 400));

        assertThat(eventsOfTheTenant()).isEqualTo(events);
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status().name()).isEqualTo("CLOSED");
    }
}
