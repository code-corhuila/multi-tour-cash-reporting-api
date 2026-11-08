package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * C level, AC-CJ.5 of the portal and registerCashCorrection of the contract: a correction note is recorded beside the
 * closure of a closed cash, never changes it, is for the Administrator only and replays with the same key.
 * Responses are validated against openapi.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class CashCorrectionControllersTest extends WriteControllersSupport {

    private static final String DAY = "/cash-days/2026-11-03";
    private static final String MOVEMENT = "{\"movementType\":\"OPERATIONAL_EXPENSE\",\"paymentMedium\":\"CASH\","
            + "\"concept\":\"Compra\",\"amountCents\":10000}";
    private static final String NOTE = "{\"justification\":\"Se corrige la descripcion de un gasto\"}";

    private void closedDay() throws Exception {
        seedOpen(TODAY, 100_000);
        answered(write(DAY + "/movements", "move-key-001", MOVEMENT), 201);
        answered(write(DAY + "/close", "close-key-001", null), 200);
    }

    @Test
    void aCorrectionIsRecordedBesideTheClosureAndChangesNothing() throws Exception {
        closedDay();
        long closuresBefore = closures.all(tenant, TODAY).size();

        mvc.perform(write(DAY + "/corrections", KEY, NOTE)).andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
        JsonNode note = answered(write(DAY + "/corrections", "corr-key-0002", NOTE), 201);

        assertThat(note.path("correctionId").asText()).isNotBlank();
        assertThat(note.path("businessDate").asText()).isEqualTo("2026-11-03");
        assertThat(note.path("justification").asText()).isEqualTo("Se corrige la descripcion de un gasto");
        assertThat(note.path("registeredBy").path("userId").asText()).isEqualTo(TestKeys.ACCOUNT);
        assertThat(closures.all(tenant, TODAY)).hasSize((int) closuresBefore);
        assertThat(sessions.find(tenant, TODAY).orElseThrow().status().name()).isEqualTo("CLOSED");
    }

    @Test
    void theSameKeyAnswers200WithTheSameCorrectionAndWritesNothingTwice() throws Exception {
        closedDay();
        JsonNode first = answered(write(DAY + "/corrections", KEY, NOTE), 201);

        JsonNode replay = answered(write(DAY + "/corrections", KEY, NOTE), 200);

        assertThat(replay.path("correctionId").asText()).isEqualTo(first.path("correctionId").asText());
    }

    @Test
    void aMissingOrBlankJustificationAnswers400ReasonRequired() throws Exception {
        closedDay();
        for (String body : new String[] {"{}", "{\"justification\":\"   \"}"}) {
            JsonNode refused = answered(write(DAY + "/corrections", KEY, body), 400);
            assertThat(errorOf(refused)).isEqualTo("REASON_REQUIRED");
        }
    }

    @Test
    void anOpenDayAnswers422AndADayWithoutCashAnswers404() throws Exception {
        seedOpen(TODAY, 100_000);
        assertThat(errorOf(answered(write(DAY + "/corrections", KEY, NOTE), 422))).isEqualTo("INVALID_STATUS_TRANSITION");

        JsonNode missing = answered(write("/cash-days/2026-11-01/corrections", "corr-key-0003", NOTE), 404);
        assertThat(errorOf(missing)).isEqualTo("NOT_FOUND");
    }

    @Test
    void onlyTheAdministratorRegistersACorrection() throws Exception {
        closedDay();
        for (String role : new String[] {"OPERATIONAL_COLLABORATOR", "ACCOUNTANT", "ANALYST", "MANAGER"}) {
            String token = token(role, "MANAGE_CASH");
            assertThat(errorOf(answered(write(DAY + "/corrections", token, KEY, NOTE), 403))).isEqualTo("FORBIDDEN");
        }
    }
}
