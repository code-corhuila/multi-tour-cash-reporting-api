package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyTotals;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryMonthlyReportSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * C level, listMonthlyReports, getMonthlyReportSummary and listMonthlyConsolidations of HU-CSH-003 (AC-RM.1 to AC-RM.6): every
 * response is validated against openapi/openapi.yaml. The projection is the in-memory source; each test works with a tenant of
 * its own and two tenants prove the isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class MonthlyReportControllersTest {

    private static final String BASE = "/api/v1/cash-reporting";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] READERS =
            {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST"};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private InMemoryMonthlyReportSource source;

    private final String tenant = UUID.randomUUID().toString();

    private static String token(String tenantOfToken, String role) {
        Map<String, Object> claims = TestKeys.claims(role);
        claims.put("tenantId", tenantOfToken);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private MockHttpServletRequestBuilder read(String path, String role) {
        return get(BASE + path).header("Authorization", "Bearer " + token(tenant, role));
    }

    private JsonNode answered(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andReturn();
        ContractCheck.responseMatches(result);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static MonthlyPeriod month(int month, long cashIncome, long transferIncome, long payments, long expenses,
            long refunds, int movements, int cancellations, long costs, Long sales) {
        MonthlyTotals totals = new MonthlyTotals(Cents.of(cashIncome), Cents.of(transferIncome), Cents.of(payments),
                Cents.of(expenses), Cents.of(refunds), movements);
        return new MonthlyPeriod(ReportedPeriod.of(YearMonth.of(2026, month)), totals, cancellations, Cents.of(costs),
                sales == null ? null : Cents.of(sales));
    }

    private void seed() {
        source.put(tenant, month(9, 100_000, 29_000, 0, 25_000, 15_000, 9, 2, 48_000, 415_000L));
        source.put(tenant, month(10, 10_000, 0, 20_000, 0, 0, 1, 0, 0, null));
    }

    @Test
    void theReportListsTheEightFieldsNewestFirstWithTheCurrentPeriodInZerosAndTheTotalMayBeNegative() throws Exception {
        seed();

        JsonNode report = answered(read("/monthly-reports", "ACCOUNTANT"), 200);

        assertThat(report.path("data").get(0).path("reportedPeriod").asText()).isEqualTo("2026-11");
        JsonNode october = report.path("data").get(1);
        assertThat(october.path("reportedPeriod").asText()).isEqualTo("2026-10");
        assertThat(october.path("consolidatedCashTotalCents").asLong()).isEqualTo(-10_000);
        JsonNode september = report.path("data").get(2);
        assertThat(september.path("periodIncomeCents").asLong()).isEqualTo(129_000);
        assertThat(september.path("operationalExpensesCents").asLong()).isEqualTo(25_000);
        assertThat(september.path("executedRefundsCents").asLong()).isEqualTo(15_000);
        assertThat(september.path("consolidatedCashTotalCents").asLong()).isEqualTo(89_000);
        assertThat(september.path("registeredCancellations").asInt()).isEqualTo(2);
        assertThat(september.path("registeredOperationalCostsCents").asLong()).isEqualTo(48_000);
        assertThat(report.path("meta").path("total").asInt()).isEqualTo(3);
    }

    @Test
    void aTenantWithoutPeriodsGetsTheCurrentPeriodInZerosAndNeverAnError() throws Exception {
        JsonNode report = answered(read("/monthly-reports", "MANAGER"), 200);

        assertThat(report.path("data")).hasSize(1);
        assertThat(report.path("data").get(0).path("reportedPeriod").asText()).isEqualTo("2026-11");
        assertThat(report.path("data").get(0).path("consolidatedCashTotalCents").asLong()).isZero();
    }

    @Test
    void theConsolidationAddsTheMovementsAndTheSplitOfTheIncome() throws Exception {
        seed();

        JsonNode september = answered(read("/monthly-consolidations", "ANALYST"), 200).path("data").get(2);

        assertThat(september.path("individualMovementsCounted").asInt()).isEqualTo(9);
        assertThat(september.path("cashIncomeCents").asLong()).isEqualTo(100_000);
        assertThat(september.path("transferIncomeCents").asLong()).isEqualTo(29_000);
        assertThat(september.path("periodIncomeCents").asLong()).isEqualTo(129_000);
    }

    @Test
    void theSummaryOfAPeriodAndTheCurrentPeriodByDefault() throws Exception {
        seed();

        JsonNode asked = answered(read("/monthly-reports/summary", "TENANT_ADMINISTRATOR").param("period", "2026-09"), 200);
        assertThat(asked.path("confirmedSalesCents").asLong()).isEqualTo(415_000);
        assertThat(asked.path("periodIncomeCents").asLong()).isEqualTo(129_000);
        assertThat(asked.path("operationalCostsCents").asLong()).isEqualTo(48_000);
        assertThat(asked.path("cancellations").asInt()).isEqualTo(2);

        JsonNode latest = answered(read("/monthly-reports/summary", "TENANT_ADMINISTRATOR"), 200);
        assertThat(latest.path("reportedPeriod").asText()).isEqualTo("2026-11");
        assertThat(latest.has("confirmedSalesCents")).isTrue();
        assertThat(latest.path("confirmedSalesCents").isNull()).isTrue();
    }

    @Test
    void aBadPeriodOrAPageOutOfRangeIsA400() throws Exception {
        assertThat(answered(read("/monthly-reports/summary", "MANAGER").param("period", "2026-13"), 400)
                .path("details").get(0).path("field").asText()).isEqualTo("period");
        answered(read("/monthly-reports/summary", "MANAGER").param("period", "september"), 400);
        answered(read("/monthly-reports", "MANAGER").param("limit", "0"), 400);
        answered(read("/monthly-consolidations", "MANAGER").param("page", "0"), 400);
    }

    @Test
    void theFiveTenantRolesReadAndAnythingElseIs403() throws Exception {
        for (String role : READERS) {
            answered(read("/monthly-reports", role), 200);
            answered(read("/monthly-consolidations", role), 200);
            answered(read("/monthly-reports/summary", role), 200);
        }
        for (String role : new String[] {"FINAL_CUSTOMER", "PLATFORM_ADMINISTRATOR", "SOMETHING_ELSE"}) {
            answered(read("/monthly-reports", role), 403);
            answered(read("/monthly-consolidations", role), 403);
            answered(read("/monthly-reports/summary", role), 403);
        }
    }

    @Test
    void aMissingOrInvalidTokenIs401() throws Exception {
        answered(get(BASE + "/monthly-reports"), 401);
        answered(get(BASE + "/monthly-consolidations").header("Authorization", "Bearer not-a-token"), 401);
        answered(get(BASE + "/monthly-reports/summary"), 401);
    }

    @Test
    void twoTenantsNeverSeeThePeriodsOfEachOther() throws Exception {
        String other = UUID.randomUUID().toString();
        seed();
        source.put(other, month(10, 7_000, 0, 0, 0, 0, 1, 0, 0, null));

        MockHttpServletRequestBuilder asOther =
                get(BASE + "/monthly-reports").header("Authorization", "Bearer " + token(other, "MANAGER"));
        JsonNode theirs = answered(asOther, 200);

        assertThat(theirs.path("data")).hasSize(2);
        assertThat(theirs.path("data").get(1).path("periodIncomeCents").asLong()).isEqualTo(7_000);
        assertThat(answered(read("/monthly-reports", "MANAGER"), 200).path("data")).hasSize(3);
    }
}
