package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxDelivery;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxEntry;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.Objects;

/**
 * What job 7 asks of the outbox of this service (HU-WRK-007, G-RL7-1, ADR-004 D4): the pending events, the mark of a delivered one and the record of a
 * failed delivery. Only a service; it writes the delivery state of the row and nothing else, and a repeat answers the state as it stands. The table has
 * no retry time, so a pending event is offered again on every run until the twentieth failed attempt ends it as FAILED.
 */
public final class RelayOutbox {

    /** The same ceiling as the other publishers: after this many failed attempts the event is FAILED. */
    public static final int MAX_ATTEMPTS = 20;
    private static final int RETRIES = 3;

    private final OutboxDelivery outbox;
    private final Clock clock;

    public RelayOutbox(OutboxDelivery outbox, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Page<OutboxEntry> pending(Actor actor, PageRequest request) {
        CashGuards.requireService(actor);
        return outbox.pending(request);
    }

    public OutboxEntry markPublished(Actor actor, String eventId) {
        CashGuards.requireService(actor);
        if (load(eventId).status().equals("PENDING")) {
            outbox.markPublished(eventId, clock.now());
        }
        return load(eventId);
    }

    public OutboxEntry recordAttempt(Actor actor, String eventId) {
        CashGuards.requireService(actor);
        for (int tries = 0; tries < RETRIES; tries++) {
            OutboxEntry found = load(eventId);
            if (!found.status().equals("PENDING")) {
                return found;
            }
            if (outbox.recordAttempt(eventId, found.attempts(), found.attempts() + 1 >= MAX_ATTEMPTS)) {
                break;
            }
        }
        return load(eventId);
    }

    private OutboxEntry load(String eventId) {
        return outbox.find(eventId).orElseThrow(() -> new CashRuleViolation.NotFound("There is no event " + eventId));
    }
}
