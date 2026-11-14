package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * C level, getInternalCashIncomeOfPayment (AC-WF2-11 of HU-WFL-002): the saga register-payment asks whether the income
 * of a payment exists after an unknown outcome of its step 1. Only a SERVICE_TOKEN, the tenant of the path, every answer
 * checked against the contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class InternalCashIncomeOfPaymentControllersTest extends WriteControllersSupport {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String PAYMENT = "6f1c1c64-0000-4000-8000-0000000000c1";
    private static final String INCOME = "{\"businessDate\":\"2026-11-03\",\"paymentMedium\":\"TRANSFER\","
            + "\"amountCents\":25000,\"reservationId\":\"" + RESERVATION + "\",\"concept\":\"Abono de la reserva\","
            + "\"paymentId\":\"" + PAYMENT + "\"}";

    private String serviceToken() {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", "multi-tour-workflow-register-payment");
        claims.remove("tenantId");
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private JsonNode recordIncome(String forTenant, String key) throws Exception {
        return answered(post(BASE + "/internal/tenants/" + forTenant + "/cash-movements")
                .header("Authorization", "Bearer " + serviceToken()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(INCOME), 201);
    }

    private MockHttpServletRequestBuilder read(String forTenant, String payment, String token) {
        MockHttpServletRequestBuilder request =
                get(BASE + "/internal/tenants/" + forTenant + "/payments/" + payment + "/cash-income");
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    @Test
    void theIncomeOfThePaymentIsAnsweredToTheSaga() throws Exception {
        JsonNode recorded = recordIncome(tenant, "saga-income-of-payment-01");

        JsonNode read = answered(read(tenant, PAYMENT, serviceToken()), 200);

        assertThat(read).isEqualTo(recorded);
        assertThat(read.path("movementType").asText()).isEqualTo("INCOME");
        assertThat(read.path("amountCents").asLong()).isEqualTo(25_000);
    }

    @Test
    void aPaymentWithoutIncomeIsNotFound() throws Exception {
        recordIncome(tenant, "saga-income-of-payment-04");

        JsonNode refused = answered(read(tenant, UUID.randomUUID().toString(), serviceToken()), 404);

        assertThat(errorOf(refused)).isEqualTo("NOT_FOUND");
        answered(read(tenant, PAYMENT, serviceToken()), 200);
    }

    @Test
    void theIncomeOfAnotherTenantIsNeverSeen() throws Exception {
        String other = UUID.randomUUID().toString();
        recordIncome(other, "saga-income-of-payment-02");

        JsonNode refused = answered(read(tenant, PAYMENT, serviceToken()), 404);
        JsonNode unknown = answered(read(tenant, UUID.randomUUID().toString(), serviceToken()), 404);

        assertThat(errorOf(refused)).isEqualTo("NOT_FOUND");
        assertThat(refused.path("message")).isEqualTo(unknown.path("message"));
        assertThat(answered(read(other, PAYMENT, serviceToken()), 200).path("movementType").asText()).isEqualTo("INCOME");
    }

    @Test
    void aPersonTokenIsRefusedAndNoTokenIsUnauthorized() throws Exception {
        recordIncome(tenant, "saga-income-of-payment-03");

        answered(read(tenant, PAYMENT, token("TENANT_ADMINISTRATOR")), 403);
        answered(read(tenant, PAYMENT, null), 401);
        answered(read(tenant, PAYMENT, serviceToken()), 200);
    }

    @Test
    void aMalformedIdentifierIsABadRequest() throws Exception {
        assertThat(errorOf(answered(read(tenant, "not-a-uuid", serviceToken()), 400))).isEqualTo("VALIDATION_ERROR");
        assertThat(errorOf(answered(read("not-a-uuid", PAYMENT, serviceToken()), 400))).isEqualTo("VALIDATION_ERROR");
    }
}
