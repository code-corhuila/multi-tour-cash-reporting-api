package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D level, RN-DB8, AC-DB.9 and AC-DB.10: the events are at least once and not ordered. A state event carries the full new
 * state and its {@code occurredAt}; an older one is ignored, equal instants are decided by the event id so the result is
 * deterministic, and the result of any order equals that of the in-order delivery.
 */
class ReservationFactOrderTest {

    private static final Instant T1 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-02T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant T4 = Instant.parse("2026-11-04T10:00:00Z");

    private static List<ReservationEvent> history() {
        return List.of(
                ReservationEvent.created("e1", "r1", T1),
                ReservationEvent.state("e2", "r1", ReservationStatus.CONFIRMED, "DEPOSIT_RECEIVED", T2),
                ReservationEvent.state("e3", "r1", ReservationStatus.CONFIRMED, "BALANCE_PENDING", T3),
                ReservationEvent.state("e4", "r1", ReservationStatus.CANCELLED, null, T4));
    }

    private static ReservationFact apply(List<ReservationEvent> events) {
        ReservationFact fact = ReservationFact.empty("r1");
        for (ReservationEvent event : events) {
            fact = fact.apply(event);
        }
        return fact;
    }

    private static void permutations(List<ReservationEvent> rest, List<ReservationEvent> chosen, List<List<ReservationEvent>> out) {
        if (rest.isEmpty()) {
            out.add(List.copyOf(chosen));
            return;
        }
        for (int i = 0; i < rest.size(); i++) {
            List<ReservationEvent> next = new ArrayList<>(rest);
            chosen.add(next.remove(i));
            permutations(next, chosen, out);
            chosen.remove(chosen.size() - 1);
        }
    }

    @Test
    void anOlderStateEventIsIgnored() {
        ReservationFact fact = ReservationFact.empty("r1")
                .apply(ReservationEvent.state("e2", "r1", ReservationStatus.CONFIRMED, "PAID", T2))
                .apply(ReservationEvent.state("e1", "r1", ReservationStatus.PENDING_PAYMENT, null, T1));

        assertThat(fact.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(fact.financialSituation()).isEqualTo("PAID");
        assertThat(fact.lastEventOccurredAt()).isEqualTo(T2);
    }

    @Test
    void equalInstantsAreDecidedByTheEventIdSoTheResultIsDeterministic() {
        ReservationEvent a = ReservationEvent.state("e-a", "r1", ReservationStatus.CONFIRMED, "PAID", T2);
        ReservationEvent b = ReservationEvent.state("e-b", "r1", ReservationStatus.CANCELLED, null, T2);

        assertThat(ReservationFact.empty("r1").apply(a).apply(b)).isEqualTo(ReservationFact.empty("r1").apply(b).apply(a));
        assertThat(ReservationFact.empty("r1").apply(a).apply(b).status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void theSameEventTwiceChangesNothing() {
        ReservationEvent confirmed = ReservationEvent.state("e2", "r1", ReservationStatus.CONFIRMED, "PAID", T2);
        ReservationFact once = ReservationFact.empty("r1").apply(confirmed);

        assertThat(once.apply(confirmed)).isEqualTo(once);
    }

    @Test
    void theFactEqualsThatOfTheInOrderDeliveryForEveryPermutationOfAFourEventHistory() {
        ReservationFact inOrder = apply(history());
        List<List<ReservationEvent>> all = new ArrayList<>();
        permutations(history(), new ArrayList<>(), all);

        assertThat(all).hasSize(24);
        assertThat(inOrder.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(inOrder.createdAt()).isEqualTo(T1);
        assertThat(inOrder.cancelledAt()).isEqualTo(T4);
        for (List<ReservationEvent> order : all) {
            assertThat(apply(order)).as(order.toString()).isEqualTo(inOrder);
        }
    }

    @Test
    void aCreationEventArrivingLateStillFixesTheCreationInstant() {
        ReservationFact fact = ReservationFact.empty("r1")
                .apply(ReservationEvent.state("e2", "r1", ReservationStatus.CONFIRMED, "PAID", T2))
                .apply(ReservationEvent.created("e1", "r1", T1));

        assertThat(fact.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(fact.createdAt()).isEqualTo(T1);
    }

    @Test
    void aReservationThatWasNeverCancelledHasNoCancellationInstant() {
        assertThat(apply(history().subList(0, 2)).cancelledAt()).isNull();
    }
}
