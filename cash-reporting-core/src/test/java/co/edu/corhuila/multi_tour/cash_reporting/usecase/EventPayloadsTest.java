package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The content of the events that 02-domain/domain-events.md fixes: the opening names its actor, the closings carry the
 * totals, the income and the refund carry actor and time, the refund its authorization. Ids and amounts only, never a name.
 */
class EventPayloadsTest {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String AUTHORIZATION = "6f1c1c64-0000-4000-8000-000000000002";
    private final CashFixture world = new CashFixture();

    private Map<String, Object> last(String eventType) {
        var events = world.outbox.events().stream().filter(event -> event.eventType().equals(eventType)).toList();
        assertThat(events).as(eventType).isNotEmpty();
        return events.get(events.size() - 1).payload();
    }

    @Test
    void theOpeningNamesItsActor() {
        world.openCashDay().execute(ADMIN, TODAY, 100_000L, "key-open-0001");

        assertThat(last("CashSessionOpened")).containsEntry("actorId", "admin-1");
    }

    @Test
    void theFormalClosingCarriesTheTotalsTheExpectedCashAndWhoClosed() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 100_000L));
        world.registerCashMovement().execute(ADMIN, MovementSource.MANUAL, TODAY, MovementType.OPERATIONAL_EXPENSE,
                PaymentMedium.CASH, "Compra", 20_000L, "key-expense-0001");
        world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");

        Map<String, Object> payload = last("CashSessionClosed");

        assertThat(payload).containsEntry("incomeCents", 0L).containsEntry("operationalPaymentsCents", 0L)
                .containsEntry("expensesCents", 20_000L).containsEntry("refundsCents", 0L)
                .containsEntry("totalCents", 80_000L).containsEntry("expectedCashCents", 80_000L)
                .containsEntry("closedBy", "admin-1");
    }

    @Test
    void theAutomaticClosingCarriesTheTotalsAndTheTime() {
        world.sessions.seed(CashSession.automatic(TENANT, YESTERDAY, BOGOTA));

        world.autoCloseCashDay().execute(WORKER, TENANT, YESTERDAY);

        Map<String, Object> payload = last("CashSessionAutoClosed");
        assertThat(payload).containsEntry("totalCents", 0L).containsEntry("incomeCents", 0L)
                .containsEntry("closedAt", NOW.toString());
        assertThat(payload).doesNotContainKey("expectedCashCents");
    }

    @Test
    void theIncomeCarriesItsActorAndItsTime() {
        world.recordSagaIncome().execute(WORKER, TENANT, "person-1", TODAY, PaymentMedium.TRANSFER, "Abono", 5_000L,
                RESERVATION, "key-income-0001");

        assertThat(last("CashIncomeRecorded")).containsEntry("actorId", "person-1")
                .containsEntry("registeredAt", NOW.toString()).containsEntry("reservationId", RESERVATION);
    }

    @Test
    void theRefundCarriesItsExecutorAndItsAuthorization() {
        world.recordExecutedRefund().register(WORKER, TENANT, "person-1", TODAY, PaymentMedium.TRANSFER, "Devolucion",
                3_000L, RESERVATION, AUTHORIZATION, "key-refund-0001");

        assertThat(last("RefundExecuted")).containsEntry("executorId", "person-1")
                .containsEntry("refundAuthorizationId", AUTHORIZATION).containsEntry("registeredAt", NOW.toString());
    }
}
