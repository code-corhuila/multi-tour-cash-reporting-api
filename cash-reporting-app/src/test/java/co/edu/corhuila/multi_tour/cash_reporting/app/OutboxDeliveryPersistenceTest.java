package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxDelivery;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxEntry;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** I level (HU-WRK-007, G-RL7-1): the delivery side of the outbox against the migrated PostgreSQL, as the application user: the writes respect the table check. */
@RequiresTestDatabase
class OutboxDeliveryPersistenceTest extends PersistenceTestBase {

    private static final Instant AT = Instant.parse("2026-11-11T12:00:00Z");

    @Autowired
    OutboxDelivery delivery;

    private String pendingEvent(String tenant) {
        outbox.append(tenant, "CashSessionOpened", newId(), Map.of("businessDate", "2026-11-11"));
        return jdbc.queryForObject("select outbox_event_id::text from cash_reporting.outbox_event where tenant_id = ?::uuid", String.class, tenant);
    }

    @Test
    void aPendingEventIsListedAndPublishingItSetsTheStatusAndTheInstantTogether() {
        String id = pendingEvent(newId());

        assertThat(delivery.pending(new PageRequest(1, 100)).items()).extracting(OutboxEntry::eventId).contains(id);
        assertThat(delivery.markPublished(id, AT)).isTrue();

        OutboxEntry published = delivery.find(id).orElseThrow();
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isEqualTo(AT);
        assertThat(delivery.pending(new PageRequest(1, 100)).items()).extracting(OutboxEntry::eventId).doesNotContain(id);
        assertThat(delivery.markPublished(id, AT.plusSeconds(1))).as("a published event does not change again").isFalse();
    }

    @Test
    void anAttemptIsCountedOnlyAgainstTheAttemptsTheCallerSawAndTheLastOneFailsTheEvent() {
        String id = pendingEvent(newId());

        assertThat(delivery.recordAttempt(id, 0, false)).isTrue();
        assertThat(delivery.recordAttempt(id, 0, false)).as("a stale count changes nothing").isFalse();
        assertThat(delivery.recordAttempt(id, 1, true)).isTrue();

        OutboxEntry failed = delivery.find(id).orElseThrow();
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.attempts()).isEqualTo(2);
        assertThat(failed.publishedAt()).isNull();
        assertThat(delivery.markPublished(id, AT)).isFalse();
    }
}
