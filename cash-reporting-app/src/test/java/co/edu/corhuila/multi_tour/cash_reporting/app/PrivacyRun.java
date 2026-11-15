package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A real run of the routes with values that must never leave the request (a concept, a reason, two amounts, the tokens):
 * the tests of logs and counters read what the run left behind. In-memory world, fixed clock, one tenant per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
abstract class PrivacyRun extends WriteControllersSupport {

    static final String CONCEPT = "Pago guia Zorro Quintero";
    static final String REASON = "Motivo reservado del cierre erroneo";
    static final String BASE_CENTS = "987654321";
    static final String AMOUNT_CENTS = "456789012";
    private static final String DAY = "/cash-days/2026-11-03";

    /** A whole day plus refusals, so every kind of line is written: ok, rule, validation, forbidden, unauthorized. */
    List<String> run() throws Exception {
        String admin = token("TENANT_ADMINISTRATOR");
        String collaborator = token("OPERATIONAL_COLLABORATOR");
        String expense = "{\"movementType\":\"OPERATIONAL_EXPENSE\",\"paymentMedium\":\"CASH\",\"concept\":\""
                + CONCEPT + "\",\"amountCents\":" + AMOUNT_CENTS + "}";
        answered(write(DAY + "/movements", admin, "priv-key-0001", expense), 422);
        answered(write(DAY + "/open", admin, "priv-key-0002", "{\"openingBaseCents\":" + BASE_CENTS + "}"), 200);
        answered(write(DAY + "/open", admin, "priv-key-0003", null), 422);
        answered(write(DAY + "/movements", admin, "priv-key-0004", expense), 201);
        answered(write(DAY + "/movements", admin, "priv-key-0005", expense.replace("}", ",\"status\":\"PENDING\"}")), 400);
        answered(write(DAY + "/open", collaborator, "priv-key-0006", null), 403);
        answered(write(DAY + "/close", admin, "priv-key-0007", null), 200);
        answered(write(DAY + "/close", admin, "priv-key-0008", null), 422);
        answered(write(DAY + "/movements", admin, "priv-key-0009", expense.replace("OPERATIONAL_EXPENSE", "INCOME").replace("CASH", "TRANSFER")), 201);
        answered(write(DAY + "/reopen", admin, "priv-key-0010", "{\"reason\":\"" + REASON + "\"}"), 200);
        answered(write(DAY + "/reopen", admin, "priv-key-0011", "{\"reason\":\"  \"}"), 400);
        mvc.perform(get(BASE + DAY).header("Authorization", "Bearer " + admin + "x"));
        return List.of(admin, collaborator, admin + "x");
    }
}
