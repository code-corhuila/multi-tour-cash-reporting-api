package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import org.junit.jupiter.api.Test;

/** The compensation of the saga register-payment: a new movement linked to the one it reverses, never an update (docs 843, 848). */
class ReverseCashMovementTest {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String AUTHORIZATION = "6f1c1c64-0000-4000-8000-000000000002";
    private final CashFixture world = new CashFixture();

    private RecordedMovement income(PaymentMedium medium, String key) {
        return world.recordSagaIncome()
                .execute(WORKER, TENANT, null, TODAY, medium, "Abono", 25_000L, RESERVATION, key)
                .value();
    }

    private RecordedMovement refund(String key) {
        return world.recordExecutedRefund()
                .register(WORKER, TENANT, null, TODAY, PaymentMedium.TRANSFER, "Devolucion", 10_000L, RESERVATION,
                        AUTHORIZATION, key)
                .value();
    }

    private Outcome<RecordedMovement> reverse(String movementId, String key) {
        return world.reverseCashMovement().execute(WORKER, TENANT, null, movementId, "Falla el registro del pago", key);
    }

    @Test
    void anIncomeIsReversedByARefundOfTheSameAmountMediumAndReservationLinkedToIt() {
        RecordedMovement original = income(PaymentMedium.TRANSFER, "key-income-0001");

        Outcome<RecordedMovement> done = reverse(original.id(), "key-reverse-0001");

        RecordedMovement compensating = done.value();
        assertThat(done.replayed()).isFalse();
        assertThat(compensating.movement().type()).isEqualTo(MovementType.REFUND);
        assertThat(compensating.movement().amount()).isEqualTo(original.movement().amount());
        assertThat(compensating.movement().medium()).isEqualTo(PaymentMedium.TRANSFER);
        assertThat(compensating.refund().reservationId()).isEqualTo(RESERVATION);
        assertThat(compensating.reversesMovementId()).isEqualTo(original.id());
        assertThat(compensating.concept()).isEqualTo("Falla el registro del pago");
        assertThat(world.movements.findById(TENANT, original.id())).contains(original);
        assertThat(world.outbox.events()).extracting(event -> event.eventType()).endsWith("RefundExecuted");
    }

    @Test
    void aRefundIsReversedByAnIncomeLinkedToIt() {
        RecordedMovement original = refund("key-refund-0001");

        RecordedMovement compensating = reverse(original.id(), "key-reverse-0002").value();

        assertThat(compensating.movement().type()).isEqualTo(MovementType.INCOME);
        assertThat(compensating.movement().amount()).isEqualTo(original.movement().amount());
        assertThat(compensating.reservationId()).isEqualTo(RESERVATION);
        assertThat(compensating.refund()).isNull();
        assertThat(compensating.reversesMovementId()).isEqualTo(original.id());
        assertThat(world.outbox.events()).extracting(event -> event.eventType()).endsWith("CashIncomeRecorded");
    }

    @Test
    void aRepeatedKeyAnswersTheStoredReversalAsAReplay() {
        RecordedMovement original = income(PaymentMedium.TRANSFER, "key-income-0003");
        Outcome<RecordedMovement> first = reverse(original.id(), "key-reverse-0003");

        Outcome<RecordedMovement> again = reverse(original.id(), "key-reverse-0003");

        assertThat(again.replayed()).isTrue();
        assertThat(again.value()).isEqualTo(first.value());
    }

    @Test
    void aMovementIsReversedOnlyOnce() {
        RecordedMovement original = income(PaymentMedium.TRANSFER, "key-income-0004");
        reverse(original.id(), "key-reverse-0004");
        int writes = world.movements.writes();

        assertThatThrownBy(() -> reverse(original.id(), "key-reverse-0005"))
                .isInstanceOf(CashRuleViolation.RuleViolation.class)
                .hasMessageContaining("already");

        assertThat(world.movements.writes()).isEqualTo(writes);
    }

    @Test
    void anUnknownMovementIsNotFound() {
        assertThatThrownBy(() -> reverse("movement-unknown", "key-reverse-0006"))
                .isInstanceOf(CashRuleViolation.NotFound.class);
    }

    @Test
    void onlyAnIncomeOrARefundCanBeReversed() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L));
        RecordedMovement expense = world.registerCashMovement()
                .execute(ADMIN, MovementSource.MANUAL, TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH,
                        "Compra", 1_000L, "key-expense-0001");

        assertThatThrownBy(() -> reverse(expense.id(), "key-reverse-0007"))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);
    }

    @Test
    void aCashIncomeOnADayThatWasClosedCannotBeCompensatedInCash() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L));
        RecordedMovement original = income(PaymentMedium.CASH, "key-income-0008");
        world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");

        assertThatThrownBy(() -> reverse(original.id(), "key-reverse-0008"))
                .isInstanceOf(CashRuleViolation.AlreadyClosed.class);
    }

    @Test
    void aTransferIsCompensatedAfterTheClosingWithoutReopeningTheDay() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L));
        RecordedMovement original = income(PaymentMedium.TRANSFER, "key-income-0009");
        world.closeCashDay().execute(ADMIN, TODAY, "key-close-0002");

        RecordedMovement compensating = reverse(original.id(), "key-reverse-0009").value();

        assertThat(compensating.afterClosing()).isTrue();
    }

    @Test
    void aPersonCannotReverseAndTheReasonIsRequired() {
        RecordedMovement original = income(PaymentMedium.TRANSFER, "key-income-0010");

        assertThatThrownBy(() -> world.reverseCashMovement()
                        .execute(ADMIN, TENANT, null, original.id(), "Motivo", "key-reverse-0010"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThatThrownBy(() -> world.reverseCashMovement()
                        .execute(WORKER, TENANT, null, original.id(), "  ", "key-reverse-0011"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
