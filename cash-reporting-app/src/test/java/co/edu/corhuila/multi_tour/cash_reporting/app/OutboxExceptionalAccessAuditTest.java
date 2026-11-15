package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ExceptionalAccessAuditException;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ExceptionalAccessUse;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.OutboxExceptionalAccessAudit;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryOutbox;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * HU-SEC-007 G-SEC9-1 (ADR-004 D5; Project Lead, 2026-11-11): each use of the token of an exceptional access is written to the outbox of this service as PlatformExceptionalAccessUsed, with the
 * authorization as aggregate and a payload closed to eight keys; the use is mandatory: if it cannot be written the read is not served (fail closed). No database: the writer is the in-memory fake of the core tests.
 */
class OutboxExceptionalAccessAuditTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID AUTHORIZATION = UUID.fromString("eeeeeeee-0000-4000-8000-000000000001");
    private static final String ADMIN = "eeeeeeee-0000-4000-8000-000000000002";
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";

    private static ExceptionalAccessUse use() {
        return new ExceptionalAccessUse(AUTHORIZATION, ADMIN, TENANT, "exceptional", "CASH", "GET", "/api/v1/cash-reporting/cash-days/{id}/movements", "corr-1");
    }

    @Test
    void aUseIsWrittenToTheOutboxWithItsAuthorizationAsAggregateAndAClosedPayload() {
        InMemoryOutbox outbox = new InMemoryOutbox();

        new OutboxExceptionalAccessAudit(outbox, CLOCK).used(use());

        assertThat(outbox.events()).hasSize(1);
        InMemoryOutbox.Event event = outbox.events().get(0);
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.eventType()).isEqualTo("PlatformExceptionalAccessUsed");
        assertThat(event.aggregateId()).isEqualTo(AUTHORIZATION.toString());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("authorizationId", AUTHORIZATION.toString());
        expected.put("platformAdministratorId", ADMIN);
        expected.put("tenantId", TENANT);
        expected.put("method", "GET");
        expected.put("route", "/api/v1/cash-reporting/cash-days/{id}/movements");
        expected.put("area", "CASH");
        expected.put("usedAt", "2026-11-11T10:00:00Z");
        expected.put("correlationId", "corr-1");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.payload()).isEqualTo(expected);
    }

    @Test
    void theEmergencyAccessWritesTheSameEventWithTheSameKeysInTheSameOrder() {
        InMemoryOutbox outbox = new InMemoryOutbox();

        new OutboxExceptionalAccessAudit(outbox, CLOCK).used(new ExceptionalAccessUse(AUTHORIZATION, ADMIN, TENANT, "emergency", "CASH", "GET", "/api/v1/cash-reporting/cash-days", "corr-2"));

        assertThat(outbox.events().get(0).payload().keySet()).containsExactly("authorizationId", "platformAdministratorId", "tenantId", "method", "route", "area", "usedAt", "correlationId");
    }

    @Test
    void anOutboxThatFailsFailsTheAuditSoTheReadIsNotServed() {
        OutboxWriter failing = (tenantId, eventType, aggregateId, payload) -> {
            throw new IllegalStateException("the outbox is down");
        };

        assertThatThrownBy(() -> new OutboxExceptionalAccessAudit(failing, CLOCK).used(use())).isInstanceOf(ExceptionalAccessAuditException.class);
    }
}
