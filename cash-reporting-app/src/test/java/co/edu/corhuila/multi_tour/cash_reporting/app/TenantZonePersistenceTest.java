package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.port.TenantZoneRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * I level, HU-CSH-002 (DP-API-2): the adapter of the zone of each tenant against a migrated PostgreSQL, as the application user.
 * At most one row per tenant, a later change replaces it, an older one never does, and the tenant leads every read.
 */
@RequiresTestDatabase
class TenantZonePersistenceTest extends PersistenceTestBase {

    private static final Instant T1 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    TenantZoneRepository zones;

    private final String tenant = UUID.randomUUID().toString();
    private final String other = UUID.randomUUID().toString();

    @Test
    void aTenantWithoutAZoneHasNone() {
        assertThat(zones.find(tenant)).isEmpty();
    }

    @Test
    void aZoneIsStoredAndALaterChangeReplacesItInTheSameRow() {
        assertThat(zones.save(tenant, ZoneId.of("Asia/Tokyo"), T1)).isTrue();
        assertThat(zones.save(tenant, ZoneId.of("Europe/Madrid"), T2)).isTrue();

        assertThat(zones.find(tenant)).contains(ZoneId.of("Europe/Madrid"));
        assertThat(jdbc.queryForObject("select count(*) from cash_reporting.tenant_time_zone where tenant_id = ?::uuid",
                Integer.class, tenant)).isEqualTo(1);
    }

    @Test
    void anOlderChangeNeverOverridesANewerOne() {
        zones.save(tenant, ZoneId.of("Europe/Madrid"), T2);

        assertThat(zones.save(tenant, ZoneId.of("Asia/Tokyo"), T1)).isFalse();

        assertThat(zones.find(tenant)).contains(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void aTenantNeverReadsTheZoneOfAnother() {
        zones.save(tenant, ZoneId.of("Asia/Tokyo"), T1);

        assertThat(zones.find(other)).isEmpty();
    }
}
