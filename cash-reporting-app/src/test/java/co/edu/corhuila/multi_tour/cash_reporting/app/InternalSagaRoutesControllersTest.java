package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** C level, the routes of the sagas: only a SERVICE_TOKEN, the tenant of the path, every answer checked against the contract. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class InternalSagaRoutesControllersTest extends WriteControllersSupport {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String AUTHORIZATION = "6f1c1c64-0000-4000-8000-000000000002";
    private static final String INCOME = "{\"businessDate\":\"2026-11-03\",\"paymentMedium\":\"TRANSFER\","
            + "\"amountCents\":25000,\"reservationId\":\"" + RESERVATION + "\",\"concept\":\"Abono de la reserva\"}";
    private static final String REFUND = "{\"businessDate\":\"2026-11-03\",\"outflowMedium\":\"TRANSFER\","
            + "\"amountCents\":10000,\"reservationId\":\"" + RESERVATION + "\",\"refundAuthorizationId\":\""
            + AUTHORIZATION + "\",\"reason\":\"Cancelacion con devolucion\"}";

    private String serviceToken() {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", "multi-tour-workflow-register-payment");
        claims.remove("tenantId");
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MockHttpServletRequestBuilder call(String route, String token, String key, String body) {
        MockHttpServletRequestBuilder request =
                post(BASE + "/internal/tenants/" + tenant + route).header("Authorization", "Bearer " + token);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void anIncomeIsRecordedForTheTenantOfThePathAndTheRepeatIsAReplay() throws Exception {
        JsonNode created = answered(call("/cash-movements", serviceToken(), "saga-income-01", INCOME), 201);

        assertThat(created.path("movementType").asText()).isEqualTo("INCOME");
        assertThat(created.path("amountCents").asLong()).isEqualTo(25_000);
        assertThat(answered(call("/cash-movements", serviceToken(), "saga-income-01", INCOME), 200)).isEqualTo(created);
        var stored = movements.findById(tenant, created.path("cashMovementId").asText()).orElseThrow();
        assertThat(stored.reservationId()).isEqualTo(RESERVATION);
        assertThat(stored.movement().type()).isEqualTo(MovementType.INCOME);
        assertThat(eventsOfTheTenant()).isEqualTo(2);
    }

    @Test
    void aCashIncomeWithoutOpeningIsRefusedAndNothingIsWritten() throws Exception {
        String cash = INCOME.replace("TRANSFER", "CASH");

        JsonNode refused = answered(call("/cash-movements", serviceToken(), "saga-income-02", cash), 422);

        assertThat(errorOf(refused)).isEqualTo("NO_OPEN_CASH");
        assertThat(keys.find(tenant, "saga-income-02")).isEmpty();
    }

    @Test
    void theBodyIsStrictAndTheKeyIsRequired() throws Exception {
        String extra = INCOME.replace("}", ",\"tenantId\":\"" + tenant + "\"}");
        String badReservation = INCOME.replace(RESERVATION, "not-a-uuid");
        String missing = INCOME.replace(",\"reservationId\":\"" + RESERVATION + "\"", "");

        assertThat(errorOf(answered(call("/cash-movements", serviceToken(), "saga-income-03", extra), 400)))
                .isEqualTo("VALIDATION_ERROR");
        assertThat(firstField(answered(call("/cash-movements", serviceToken(), "saga-income-04", badReservation), 400)))
                .isEqualTo("reservationId");
        assertThat(firstField(answered(call("/cash-movements", serviceToken(), "saga-income-05", missing), 400)))
                .isEqualTo("reservationId");
        answered(call("/cash-movements", serviceToken(), null, INCOME), 400);
        assertThat(movements.list(tenant, TODAY, new co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest(1, 20)).total())
                .isZero();
    }

    @Test
    void aPersonTokenIsRefusedOnTheSagaRoutes() throws Exception {
        answered(call("/cash-movements", token("TENANT_ADMINISTRATOR"), "saga-income-06", INCOME), 403);
        answered(call("/cash-refunds", token("TENANT_ADMINISTRATOR"), "saga-refund-06", REFUND), 403);
        assertThat(keys.find(tenant, "saga-income-06")).isEmpty();
    }

    @Test
    void aRefundIsRecordedWithItsAuthorizationAndCountsAsARefund() throws Exception {
        JsonNode created = answered(call("/cash-refunds", serviceToken(), "saga-refund-01", REFUND), 201);

        assertThat(created.path("movementType").asText()).isEqualTo("REFUND");
        assertThat(answered(call("/cash-refunds", serviceToken(), "saga-refund-01", REFUND), 200)).isEqualTo(created);
        var stored = movements.findById(tenant, created.path("cashMovementId").asText()).orElseThrow();
        assertThat(stored.refund().authorizationId()).isEqualTo(AUTHORIZATION);
        assertThat(stored.refund().reservationId()).isEqualTo(RESERVATION);
    }

    @Test
    void aRefundNeedsItsAuthorizationAndItsReason() throws Exception {
        String noAuthorization = REFUND.replace(",\"refundAuthorizationId\":\"" + AUTHORIZATION + "\"", "");

        assertThat(firstField(answered(call("/cash-refunds", serviceToken(), "saga-refund-02", noAuthorization), 400)))
                .isEqualTo("refundAuthorizationId");
        assertThat(keys.find(tenant, "saga-refund-02")).isEmpty();
    }

    @Test
    void theOnBehalfOfPersonIsKeptAsWhoRegistered() throws Exception {
        String person = "6f1c1c64-0000-4000-8000-000000000009";
        String body = INCOME.replace("}", ",\"onBehalfOf\":\"" + person + "\"}");

        JsonNode created = answered(call("/cash-movements", serviceToken(), "saga-income-07", body), 201);

        assertThat(movements.findById(tenant, created.path("cashMovementId").asText()).orElseThrow().registeredBy())
                .isEqualTo(person);
    }
}
