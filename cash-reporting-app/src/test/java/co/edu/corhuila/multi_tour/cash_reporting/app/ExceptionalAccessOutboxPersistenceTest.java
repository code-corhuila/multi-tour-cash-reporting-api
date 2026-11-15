package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ExceptionalAccessAudit;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ExceptionalAccessUse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** I level (HU-SEC-007 G-SEC9-1): the audit adapter against the migrated PostgreSQL, as the application user: the tenth event of the table check is accepted with its closed payload. */
@RequiresTestDatabase
class ExceptionalAccessOutboxPersistenceTest extends PersistenceTestBase {

    @Autowired
    ExceptionalAccessAudit audit;

    @Test
    void aUseIsStoredAsAPendingEventWithItsAuthorizationAsAggregateAndTheEightKeys() {
        UUID authorization = UUID.randomUUID();
        String tenant = newId();

        audit.used(new ExceptionalAccessUse(authorization, newId(), tenant, "exceptional", "CASH", "GET", "/api/v1/cash-reporting/cash-days/2026-11-03/movements", "corr-1"));

        Map<String, Object> row = jdbc.queryForMap(
                "select event_type, aggregate_id::text as aggregate_id, status, payload ->> 'route' as route from cash_reporting.outbox_event where tenant_id = ?::uuid", tenant);
        assertThat(row).containsEntry("event_type", "PlatformExceptionalAccessUsed").containsEntry("aggregate_id", authorization.toString()).containsEntry("status", "PENDING")
                .containsEntry("route", "/api/v1/cash-reporting/cash-days/2026-11-03/movements");
        List<String> keys = jdbc.queryForList("select jsonb_object_keys(payload) from cash_reporting.outbox_event where tenant_id = ?::uuid order by 1", String.class, tenant);
        assertThat(keys).containsExactly("area", "authorizationId", "correlationId", "method", "platformAdministratorId", "route", "tenantId", "usedAt");
        assertThat(jdbc.queryForObject("select (payload ->> 'usedAt')::timestamptz = occurred_at from cash_reporting.outbox_event where tenant_id = ?::uuid", Boolean.class, tenant)).isTrue();
    }
}
