package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port of the delivery side of the outbox (job 7, ADR-004 D4): the pending events across tenants and the two writes of the relay.
 * Every write is guarded by the PENDING state, so a published or failed event never changes again; false means the row was not changed.
 */
public interface OutboxDelivery {

    /** The events still PENDING, oldest first. */
    Page<OutboxEntry> pending(PageRequest request);

    Optional<OutboxEntry> find(String eventId);

    boolean markPublished(String eventId, Instant at);

    /** Counts one failed delivery, only if the event still has {@code expectedAttempts}; {@code failed} ends it as FAILED. */
    boolean recordAttempt(String eventId, int expectedAttempts, boolean failed);
}
