package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyProjection;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryDashboardFacts;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryMonthlyProjections;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * C level, receiveFactEvent for the types of the monthly report and of the zone (HU-CSH-002 AC-DB.17, HU-CSH-003 AC-03.10 and
 * AC-03.17): the cost feeds the month, the zone of the tenant changes what the service calls today, and an event that feeds
 * the dashboard and the month (a reservation confirmed with its value, a reservation cancelled) is applied to both once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class InternalMonthlyEventsRoutesControllersTest extends WriteControllersSupport {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final ReportedPeriod NOVEMBER = ReportedPeriod.of(YearMonth.of(2026, 11));

    @Autowired
    InMemoryDashboardFacts facts;
    @Autowired
    InMemoryMonthlyProjections projections;

    private String serviceToken() {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", "multi-tour-worker-event-relay");
        claims.remove("tenantId");
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private static String event(String id, String type, String at, String payload) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"" + type + "\",\"eventVersion\":1,\"aggregateId\":\"" + RESERVATION
                + "\",\"occurredAt\":\"" + at + "\",\"payload\":" + payload + "}";
    }

    private MockHttpServletRequestBuilder deliver(String tenantOfPath, String id, String body) {
        return post(BASE + "/internal/tenants/" + tenantOfPath + "/events").header("Authorization", "Bearer " + serviceToken())
                .header("Idempotency-Key", id).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder deliver(String id, String body) {
        return deliver(tenant, id, body);
    }

    private static String cost(String id, String costId, String amount) {
        return event(id, "OperationalCostRecorded", "2026-11-03T10:00:00Z",
                "{\"costId\":\"" + costId + "\",\"amountCents\":" + amount + "}");
    }

    private static String confirmed(String id, String value) {
        return event(id, "ReservationConfirmed", "2026-11-03T10:00:00Z", "{\"reservationId\":\"" + RESERVATION
                + "\",\"paymentStatus\":\"PAID\"" + (value == null ? "" : ",\"commercialValueCents\":" + value) + "}");
    }

    private static String cancelled(String id) {
        return event(id, "ReservationCancelled", "2026-11-03T11:00:00Z", "{\"reservationId\":\"" + RESERVATION + "\"}");
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private MockHttpServletRequestBuilder today(String tenantOfToken) {
        Map<String, Object> claims = TestKeys.claims("MANAGER");
        claims.put("tenantId", tenantOfToken);
        String token = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        return get(BASE + "/cash-days/current").header("Authorization", "Bearer " + token);
    }

    @Test
    void anOperationalCostFeedsTheMonthOnceWhateverTheEventThatCarriesIt() throws Exception {
        String costId = id();
        String first = id();

        answered(deliver(first, cost(first, costId, "48000")), 201);
        answered(deliver(first, cost(first, costId, "48000")), 200);
        String again = id();
        answered(deliver(again, cost(again, costId, "48000")), 201);

        MonthlyProjection month = projections.find(tenant, NOVEMBER).orElseThrow();
        assertThat(month.costs()).containsExactly(Map.entry(costId, Cents.of(48_000)));
    }

    @Test
    void aCostWithoutItsIdOrWithAnInvalidAmountIsRefusedAndNothingIsApplied() throws Exception {
        String id = id();

        assertThat(firstField(answered(deliver(id, cost(id, "", "100")), 400))).isEqualTo("costId");
        assertThat(firstField(answered(deliver(id, cost(id, id(), "-5")), 400))).isEqualTo("amountCents");
        assertThat(firstField(answered(deliver(id, cost(id, id(), "\"x\"")), 400))).isEqualTo("amountCents");
        assertThat(projections.find(tenant, NOVEMBER)).isEmpty();
    }

    @Test
    void theZoneOfATenantChangesItsDayAndOnlyItsDay() throws Exception {
        String other = UUID.randomUUID().toString();
        String id = id();
        String change = event(id, "TenantLocalizationChanged", "2026-11-03T09:00:00Z", "{\"timeZone\":\"Pacific/Kiritimati\"}");

        answered(deliver(id, change), 201);
        answered(deliver(id, change), 200);

        JsonNode mine = answered(today(tenant), 200);
        assertThat(mine.path("timeZone").asText()).isEqualTo("Pacific/Kiritimati");
        assertThat(mine.path("businessDate").asText()).isEqualTo("2026-11-04");
        JsonNode theirs = answered(today(other), 200);
        assertThat(theirs.path("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(theirs.path("businessDate").asText()).isEqualTo("2026-11-03");
    }

    @Test
    void anOlderChangeNeverOverridesANewerOne() throws Exception {
        String newer = id();
        String older = id();

        answered(deliver(newer, event(newer, "TenantLocalizationChanged", "2026-11-03T09:00:00Z", "{\"timeZone\":\"Asia/Tokyo\"}")), 201);
        answered(deliver(older, event(older, "TenantLocalizationChanged", "2026-11-02T09:00:00Z", "{\"timeZone\":\"Europe/Madrid\"}")), 201);

        assertThat(answered(today(tenant), 200).path("timeZone").asText()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void aZoneThatIsNotAnIanaNameIsRefused() throws Exception {
        String id = id();

        String bad = event(id, "TenantLocalizationChanged", "2026-11-03T09:00:00Z", "{\"timeZone\":\"Mars/Base\"}");
        assertThat(firstField(answered(deliver(id, bad), 400))).isEqualTo("timeZone");
        String missing = event(id, "TenantLocalizationChanged", "2026-11-03T09:00:00Z", "{}");
        assertThat(firstField(answered(deliver(id, missing), 400))).isEqualTo("timeZone");
        assertThat(answered(today(tenant), 200).path("timeZone").asText()).isEqualTo("America/Bogota");
    }

    @Test
    void aConfirmationWithItsValueMovesTheDashboardAndTheSalesOfTheMonthOnce() throws Exception {
        String id = id();

        answered(deliver(id, confirmed(id, "415000000")), 201);
        answered(deliver(id, confirmed(id, "415000000")), 200);

        assertThat(facts.findReservation(tenant, RESERVATION).orElseThrow().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(projections.find(tenant, NOVEMBER).orElseThrow().confirmedSales()).isEqualTo(Cents.of(415_000_000));
    }

    @Test
    void aConfirmationWithoutItsValueMovesOnlyTheDashboard() throws Exception {
        String id = id();

        answered(deliver(id, confirmed(id, null)), 201);

        assertThat(facts.findReservation(tenant, RESERVATION)).isPresent();
        assertThat(projections.find(tenant, NOVEMBER)).isEmpty();
    }

    @Test
    void aCancellationMovesTheDashboardAndTheCancellationsOfTheMonthOnce() throws Exception {
        String first = id();
        String second = id();

        answered(deliver(first, cancelled(first)), 201);
        answered(deliver(first, cancelled(first)), 200);
        answered(deliver(second, cancelled(second)), 201);

        assertThat(facts.findReservation(tenant, RESERVATION).orElseThrow().status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(projections.find(tenant, NOVEMBER).orElseThrow().cancelledReservations()).containsExactly(RESERVATION);
    }

    @Test
    void theMonthOfATenantIsNeverTheMonthOfAnother() throws Exception {
        String other = UUID.randomUUID().toString();
        String id = id();

        answered(deliver(other, id, cost(id, id(), "100")), 201);

        assertThat(projections.find(other, NOVEMBER)).isPresent();
        assertThat(projections.find(tenant, NOVEMBER)).isEmpty();
    }
}
