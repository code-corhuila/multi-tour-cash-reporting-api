package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureStatus;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryDashboardFacts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
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
 * C level, receiveFactEvent of HU-CSH-002 (AC-DB.9, AC-DB.17): only a SERVICE_TOKEN, the tenant of the path, the key is the
 * event id, 201 the first time and 200 for a repeat; every answer is checked against the contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class InternalEventsRoutesControllersTest extends WriteControllersSupport {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String DEPARTURE = "6f1c1c64-0000-4000-8000-000000000002";

    @Autowired
    InMemoryDashboardFacts facts;

    private String serviceToken() {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("SERVICE"));
        claims.put("sub", "multi-tour-worker-event-relay");
        claims.remove("tenantId");
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private static String event(String id, String type, String aggregate, String payload) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"" + type + "\",\"eventVersion\":1,\"aggregateId\":\""
                + aggregate + "\",\"occurredAt\":\"2026-11-03T10:00:00Z\",\"payload\":" + payload + "}";
    }

    private static String reservation(String id, String type, String paymentStatus) {
        return event(id, type, RESERVATION, "{\"reservationId\":\"" + RESERVATION + "\""
                + (paymentStatus == null ? "" : ",\"paymentStatus\":\"" + paymentStatus + "\"") + "}");
    }

    private MockHttpServletRequestBuilder deliver(String tenantOfPath, String token, String key, String body) {
        MockHttpServletRequestBuilder request = post(BASE + "/internal/tenants/" + tenantOfPath + "/events")
                .header("Authorization", "Bearer " + token);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder deliver(String id, String body) {
        return deliver(tenant, serviceToken(), id, body);
    }

    @Test
    void aReservationConfirmedIsAppliedAndTheRepeatOfItsKeyChangesNothing() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = reservation(id, "ReservationConfirmed", "DEPOSIT_RECEIVED");

        answered(deliver(id, body), 201);
        answered(deliver(id, body), 200);

        var fact = facts.findReservation(tenant, RESERVATION).orElseThrow();
        assertThat(fact.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(fact.financialSituation()).isEqualTo("DEPOSIT_RECEIVED");
        assertThat(facts.reservations(tenant)).hasSize(1);
    }

    @Test
    void aReservationCreatedStartsPendingPaymentAndFixesItsCreationInstant() throws Exception {
        String id = UUID.randomUUID().toString();

        answered(deliver(id, reservation(id, "ReservationCreated", null)), 201);

        var fact = facts.findReservation(tenant, RESERVATION).orElseThrow();
        assertThat(fact.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(fact.createdAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
    }

    @Test
    void aReservationCancelledLeavesItCancelled() throws Exception {
        String id = UUID.randomUUID().toString();

        answered(deliver(id, reservation(id, "ReservationCancelled", null)), 201);

        assertThat(facts.findReservation(tenant, RESERVATION).orElseThrow().status())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void theDepartureEventsAreAppliedAndTheScheduledStartIsKept() throws Exception {
        String id = UUID.randomUUID().toString();
        String payload = "{\"departureId\":\"" + DEPARTURE + "\",\"scheduledStart\":\"2026-11-03T15:00:00Z\"}";
        answered(deliver(id, event(id, "SalidaScheduled", DEPARTURE, payload)), 201);

        String cancelled = UUID.randomUUID().toString();
        String later = event(cancelled, "SalidaCancelled", DEPARTURE, "{\"departureId\":\"" + DEPARTURE + "\"}")
                .replace("10:00:00Z", "11:00:00Z");
        answered(deliver(cancelled, later), 201);

        var fact = facts.findDeparture(tenant, DEPARTURE).orElseThrow();
        assertThat(fact.status()).isEqualTo(DepartureStatus.CANCELLED);
        assertThat(fact.scheduledStart()).isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
    }

    @Test
    void theFactsAreOfTheTenantOfThePathAndNeverOfAnother() throws Exception {
        String other = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();

        answered(deliver(other, serviceToken(), id, reservation(id, "ReservationConfirmed", "PAID")), 201);

        assertThat(facts.reservations(other)).hasSize(1);
        assertThat(facts.reservations(tenant)).isEmpty();
    }

    @Test
    void theSameEventIdIsAppliedOncePerTenant() throws Exception {
        String other = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        String body = reservation(id, "ReservationConfirmed", "PAID");

        answered(deliver(tenant, serviceToken(), id, body), 201);
        answered(deliver(other, serviceToken(), id, body), 201);
        answered(deliver(tenant, serviceToken(), id, body), 200);
    }

    @Test
    void anEventTypeTheServiceDoesNotConsumeIsRefusedNamingTheType() throws Exception {
        String id = UUID.randomUUID().toString();

        for (String type : new String[] {"PaymentValidated", "SomethingElse"}) {
            JsonNode refused = answered(deliver(id, event(id, type, RESERVATION, "{}")), 400);
            assertThat(errorOf(refused)).isEqualTo("VALIDATION_ERROR");
            assertThat(firstField(refused)).isEqualTo("eventType");
        }
        assertThat(facts.reservations(tenant)).isEmpty();
    }

    @Test
    void aTenantInTheEnvelopeThatIsNotTheOneOfThePathIsRefused() throws Exception {
        String id = UUID.randomUUID().toString();
        String valid = reservation(id, "ReservationConfirmed", "PAID");
        String foreign = valid.replace("{\"eventId\"", "{\"tenantId\":\"" + UUID.randomUUID() + "\",\"eventId\"");
        String same = valid.replace("{\"eventId\"", "{\"tenantId\":\"" + tenant + "\",\"eventId\"");

        assertThat(firstField(answered(deliver(id, foreign), 400))).isEqualTo("tenantId");
        assertThat(facts.reservations(tenant)).isEmpty();
        answered(deliver(id, same), 201);
    }

    @Test
    void theEnvelopeIsCheckedAndNothingIsApplied() throws Exception {
        String id = UUID.randomUUID().toString();
        String valid = reservation(id, "ReservationConfirmed", "PAID");

        assertThat(firstField(answered(deliver(id, valid.replace("\"eventVersion\":1,", "")), 400)))
                .isEqualTo("eventVersion");
        assertThat(firstField(answered(deliver(id, valid.replace("2026-11-03T10:00:00Z", "yesterday")), 400)))
                .isEqualTo("occurredAt");
        assertThat(firstField(answered(deliver(id, valid.replace("\"eventId\":\"" + id, "\"eventId\":\"not-a-uuid")), 400)))
                .isEqualTo("eventId");
        assertThat(firstField(answered(deliver(UUID.randomUUID().toString(), valid), 400))).isEqualTo("Idempotency-Key");
        answered(deliver(null, valid), 400);
        answered(deliver(id, "[]"), 400);
        answered(deliver(id, null), 400);
        assertThat(facts.reservations(tenant)).isEmpty();
    }

    @Test
    void aBodyOfMoreThanSixteenKilobytesIsRefusedAndAnUnknownFieldIsTolerated() throws Exception {
        String id = UUID.randomUUID().toString();
        String valid = reservation(id, "ReservationConfirmed", "PAID");
        String big = valid.replace("\"paymentStatus\"", "\"note\":\"" + "x".repeat(17_000) + "\",\"paymentStatus\"");
        String extra = valid.replace("\"eventVersion\":1,", "\"eventVersion\":1,\"producer\":\"x\",");

        assertThat(firstField(answered(deliver(id, big), 400))).isEqualTo("body");
        answered(deliver(id, extra), 201);
    }

    @Test
    void aPersonTokenAnAbsentTokenAndABadTenantAreRefused() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = reservation(id, "ReservationConfirmed", "PAID");

        answered(deliver(tenant, token("TENANT_ADMINISTRATOR"), id, body), 403);
        answered(deliver(tenant, token("PLATFORM_ADMINISTRATOR"), id, body), 403);
        answered(post(BASE + "/internal/tenants/" + tenant + "/events").header("Idempotency-Key", id)
                .contentType(MediaType.APPLICATION_JSON).content(body), 401);
        answered(deliver("not-a-uuid", serviceToken(), id, body), 400);
        assertThat(facts.reservations(tenant)).isEmpty();
    }
}
