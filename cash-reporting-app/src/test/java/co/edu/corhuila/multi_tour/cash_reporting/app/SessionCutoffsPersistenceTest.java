package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.port.SessionCutoffs;
import co.edu.corhuila.multi_tour.cash_reporting.revocation.SessionCutoff;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * I level, HU-SEC-005 AC4 and HU-SEC-006 AC7 (RN-CSH-API-43 and -44): the adapter of the cutoffs against a migrated
 * PostgreSQL, as the application user. One row per account; the upsert only moves the instant forward.
 */
@RequiresTestDatabase
class SessionCutoffsPersistenceTest extends PersistenceTestBase {

    private static final Instant T10 = Instant.parse("2026-11-12T10:00:00Z");

    @Autowired
    SessionCutoffs cutoffs;

    private final String account = UUID.randomUUID().toString();
    private final String tenant = UUID.randomUUID().toString();

    private SessionCutoff cutoff(String tenantId, Instant from) {
        return new SessionCutoff(account, tenantId, from, UUID.randomUUID().toString());
    }

    @Test
    void anAccountWithoutACutoffHasNone() {
        assertThat(cutoffs.find(account)).isEmpty();
    }

    @Test
    void aCutoffIsStoredAndReadBackWithEveryField() {
        SessionCutoff first = cutoff(tenant, T10);

        assertThat(cutoffs.advance(first)).isTrue();

        assertThat(cutoffs.find(account)).contains(first);
    }

    @Test
    void aPlatformAccountKeepsItsCutoffWithNoTenant() {
        SessionCutoff first = cutoff(null, T10);

        assertThat(cutoffs.advance(first)).isTrue();

        assertThat(cutoffs.find(account)).contains(first);
    }

    @Test
    void theSameEventAgainLeavesTheRowAsItWas() {
        SessionCutoff first = cutoff(tenant, T10);
        cutoffs.advance(first);

        assertThat(cutoffs.advance(first)).isFalse();

        assertThat(cutoffs.find(account)).contains(first);
    }

    @Test
    void anOlderEventLeavesTheNewerCutoffAndALaterOneMovesItForward() {
        SessionCutoff newest = cutoff(tenant, T10);
        cutoffs.advance(newest);

        assertThat(cutoffs.advance(cutoff(tenant, T10.minusSeconds(60)))).isFalse();
        assertThat(cutoffs.find(account)).contains(newest);
        SessionCutoff later = cutoff(tenant, T10.plusSeconds(60));
        assertThat(cutoffs.advance(later)).isTrue();
        assertThat(cutoffs.find(account)).contains(later);
    }
}
