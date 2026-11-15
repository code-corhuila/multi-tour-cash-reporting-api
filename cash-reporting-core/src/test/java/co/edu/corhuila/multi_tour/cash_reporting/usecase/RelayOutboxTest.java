package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxEntry;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-WRK-007 G-RL7-1: what job 7 asks of the outbox of this service. Only a service; it moves the delivery state and nothing else. */
class RelayOutboxTest {

    private final CashFixture world = new CashFixture();

    private String append(String type) {
        world.outbox.append(TENANT, type, UUID.randomUUID().toString(), Map.of("k", "v"));
        return world.outbox.pending(new PageRequest(1, 100)).items().getLast().eventId();
    }

    @Test
    void listsThePendingEventsOldestFirstAcrossTenants() {
        world.outbox.append("tenant-2", "CashSessionOpened", UUID.randomUUID().toString(), Map.of());
        world.outbox.append(TENANT, "PlatformExceptionalAccessUsed", UUID.randomUUID().toString(), Map.of());

        var page = world.relayOutbox().pending(WORKER, PageRequest.of(1, 1));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(OutboxEntry::eventType).containsExactly("CashSessionOpened");
    }

    @Test
    void markingPublishedLeavesThePendingListAndARepeatChangesNothing() {
        String id = append("PlatformExceptionalAccessUsed");

        OutboxEntry first = world.relayOutbox().markPublished(WORKER, id);
        OutboxEntry again = world.relayOutbox().markPublished(WORKER, id);

        assertThat(first.status()).isEqualTo("PUBLISHED");
        assertThat(first.publishedAt()).isEqualTo(NOW);
        assertThat(again).isEqualTo(first);
        assertThat(world.relayOutbox().pending(WORKER, PageRequest.of(1, 100)).items()).isEmpty();
    }

    @Test
    void eachFailedAttemptIsCountedAndTheTwentiethEndsTheEventAsFailed() {
        String id = append("PlatformExceptionalAccessUsed");

        for (int attempt = 1; attempt < RelayOutbox.MAX_ATTEMPTS; attempt++) {
            OutboxEntry counted = world.relayOutbox().recordAttempt(WORKER, id);
            assertThat(counted.status()).isEqualTo("PENDING");
            assertThat(counted.attempts()).isEqualTo(attempt);
        }
        OutboxEntry last = world.relayOutbox().recordAttempt(WORKER, id);

        assertThat(last.status()).isEqualTo("FAILED");
        assertThat(last.attempts()).isEqualTo(RelayOutbox.MAX_ATTEMPTS);
        assertThat(world.relayOutbox().recordAttempt(WORKER, id)).as("a failed event answers its state").isEqualTo(last);
        assertThat(world.relayOutbox().markPublished(WORKER, id).status()).as("and cannot be published afterwards").isEqualTo("FAILED");
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThatThrownBy(() -> world.relayOutbox().markPublished(WORKER, UUID.randomUUID().toString()))
                .isInstanceOf(CashRuleViolation.NotFound.class);
        assertThatThrownBy(() -> world.relayOutbox().recordAttempt(WORKER, UUID.randomUUID().toString()))
                .isInstanceOf(CashRuleViolation.NotFound.class);
    }

    @Test
    void onlyAServiceMayUseTheThreeOperationsAndNothingIsChangedForAPerson() {
        String id = append("PlatformExceptionalAccessUsed");

        assertThatThrownBy(() -> world.relayOutbox().pending(ADMIN, PageRequest.of(1, 20))).isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThatThrownBy(() -> world.relayOutbox().markPublished(ADMIN, id)).isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThatThrownBy(() -> world.relayOutbox().recordAttempt(null, id)).isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThat(world.outbox.find(id).orElseThrow().status()).isEqualTo("PENDING");
    }
}
