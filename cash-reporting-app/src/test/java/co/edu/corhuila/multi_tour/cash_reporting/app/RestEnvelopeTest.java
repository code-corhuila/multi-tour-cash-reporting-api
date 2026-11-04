package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** C level: the token filter, the common envelope and the correlation id, in memory and without a database. */
@SpringBootTest
@AutoConfigureMockMvc
class RestEnvelopeTest {

    private static final String CASH_DAYS = "/api/v1/cash-reporting/cash-days/current";
    private static final String PROBE = "/api/v1/cash-reporting/_probe";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestConfiguration
    static class Fixed {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TestKeys.NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static String rs256(Map<String, Object> claims) {
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MvcResult refused(MockHttpServletRequestBuilder request, int status, String error) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andReturn();
        ContractCheck.responseMatches(result);
        return result;
    }

    @Test
    void aRequestWithoutTokenAnswers401() throws Exception {
        refused(get(CASH_DAYS), 401, "UNAUTHORIZED");
        refused(get(CASH_DAYS).header("Authorization", "Basic abc"), 401, "UNAUTHORIZED");
    }

    @Test
    void aTokenWithAnotherAlgorithmAnswers401() throws Exception {
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        // Signed with the trusted key and a valid SHA-256 signature: only the header's algorithm is wrong.
        for (String alg : new String[] {"RS384", "HS256", "none", "rs256"}) {
            String token = TestKeys.sign(Map.of("alg", alg), claims, TestKeys.TRUSTED, "SHA256withRSA");
            refused(bearer(get(CASH_DAYS), token), 401, "UNAUTHORIZED");
        }
        String unsigned = TestKeys.sign(Map.of("alg", "none"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        refused(bearer(get(CASH_DAYS), unsigned.substring(0, unsigned.lastIndexOf('.') + 1)), 401, "UNAUTHORIZED");
    }

    @Test
    void anExpiredTokenAnswers401() throws Exception {
        Map<String, Object> claims = TestKeys.claims("TENANT_ADMINISTRATOR");
        claims.put("exp", TestKeys.NOW.minusSeconds(1).getEpochSecond());
        refused(bearer(get(CASH_DAYS), rs256(claims)), 401, "UNAUTHORIZED");
        claims.remove("exp");
        refused(bearer(get(CASH_DAYS), rs256(claims)), 401, "UNAUTHORIZED");
    }

    @Test
    void aTokenSignedByAnotherKeyOrMalformedAnswers401() throws Exception {
        String foreign = TestKeys.sign(Map.of("alg", "RS256"), TestKeys.claims("MANAGER"), TestKeys.OTHER,
                "SHA256withRSA");
        refused(bearer(get(CASH_DAYS), foreign), 401, "UNAUTHORIZED");
        refused(bearer(get(CASH_DAYS), "not.a.jwt"), 401, "UNAUTHORIZED");
        refused(bearer(get(CASH_DAYS), "abc"), 401, "UNAUTHORIZED");
    }

    @Test
    void aTokenWithoutTenantAnswers403() throws Exception {
        Map<String, Object> claims = TestKeys.claims("PLATFORM_ADMINISTRATOR");
        claims.put("tenantId", null);
        refused(bearer(get(CASH_DAYS), rs256(claims)), 403, "FORBIDDEN");
    }

    @Test
    void aValidTokenBuildsTheActorFromItsClaims() throws Exception {
        String token = TestKeys.token("OPERATIONAL_COLLABORATOR", TestKeys.MANAGE_CASH, "Validar comprobantes");
        mvc.perform(bearer(get(PROBE + "/actor"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TestKeys.TENANT))
                .andExpect(jsonPath("$.userId").value(TestKeys.ACCOUNT))
                .andExpect(jsonPath("$.roles[0]").value("OPERATIONAL_COLLABORATOR"))
                .andExpect(jsonPath("$.permissions.length()").value(2));
    }

    @Test
    void theManageCashPermissionStringComesFromConfiguration() throws Exception {
        mvc.perform(bearer(get(PROBE + "/actor"), TestKeys.token("OPERATIONAL_COLLABORATOR", TestKeys.MANAGE_CASH)))
                .andExpect(jsonPath("$.canOpenTheCash").value(true));
        mvc.perform(bearer(get(PROBE + "/actor"), TestKeys.token("OPERATIONAL_COLLABORATOR", "another permission")))
                .andExpect(jsonPath("$.canOpenTheCash").value(false));
        mvc.perform(bearer(get(PROBE + "/actor"), TestKeys.token("OPERATIONAL_COLLABORATOR")))
                .andExpect(jsonPath("$.canOpenTheCash").value(false));
    }

    @Test
    void traceIdEqualsTheCorrelationId() throws Exception {
        MvcResult result = mvc.perform(get(CASH_DAYS).header("X-Correlation-Id", "trace-abc.123_X"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "trace-abc.123_X"))
                .andExpect(jsonPath("$.traceId").value("trace-abc.123_X")).andReturn();
        ContractCheck.responseMatches(result);
    }

    @Test
    void aMissingOrUnsafeCorrelationIdIsGeneratedAndReturned() throws Exception {
        for (String sent : new String[] {null, "bad id with spaces", "x".repeat(200)}) {
            MockHttpServletRequestBuilder request = get(CASH_DAYS);
            MvcResult result = mvc.perform(sent == null ? request : request.header("X-Correlation-Id", sent))
                    .andReturn();
            String generated = result.getResponse().getHeader("X-Correlation-Id");
            assertThat(generated).isNotBlank().isNotEqualTo(sent).matches("[A-Za-z0-9_.-]{1,128}");
            assertThat(JSON.readTree(result.getResponse().getContentAsString()).path("traceId").asText())
                    .isEqualTo(generated);
        }
    }

    @Test
    void theCorrelationIdIsInTheMdcOfTheRequest() throws Exception {
        mvc.perform(bearer(get(PROBE + "/mdc").header("X-Correlation-Id", "mdc-1"), TestKeys.token("MANAGER")))
                .andExpect(jsonPath("$.traceId").value("mdc-1"));
    }

    @Test
    void aRuleOfTheCoreAnswersItsCodeInTheCommonEnvelope() throws Exception {
        String token = TestKeys.token("TENANT_ADMINISTRATOR");
        mvc.perform(bearer(get(PROBE + "/violation/already-open").header("X-Correlation-Id", "t-1"), token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ALREADY_OPEN"))
                .andExpect(jsonPath("$.message").value("La caja de hoy ya tiene apertura operativa"))
                .andExpect(jsonPath("$.traceId").value("t-1"));
        mvc.perform(bearer(get(PROBE + "/violation/pending"), token)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PENDING_CLOSURE"));
    }

    @Test
    void anUnknownRouteAndMalformedJsonUseTheEnvelope() throws Exception {
        String token = TestKeys.token("TENANT_ADMINISTRATOR");
        mvc.perform(bearer(get("/api/v1/cash-reporting/nothing-here"), token)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND")).andExpect(jsonPath("$.traceId").isNotEmpty());
        mvc.perform(bearer(post(PROBE + "/echo"), token).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("body"));
    }

    @Test
    void anUnexpectedFailureNeverShowsItsInternals() throws Exception {
        MvcResult result = mvc.perform(bearer(get(PROBE + "/boom"), TestKeys.token("TENANT_ADMINISTRATOR")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("jdbc", "secret", "IllegalState");
        JsonNode envelope = JSON.readTree(body);
        Set<String> names = StreamSupport.stream(((Iterable<String>) envelope::fieldNames).spliterator(), false)
                .collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("error", "message", "traceId");
    }
}
