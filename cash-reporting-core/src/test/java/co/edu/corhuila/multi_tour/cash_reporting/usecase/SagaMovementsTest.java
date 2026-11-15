package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The writes of the sagas (ADR-004 D4): a service records for the tenant of the path, never through a person. */
class SagaMovementsTest {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String AUTHORIZATION = "6f1c1c64-0000-4000-8000-000000000002";
    private static final String PERSON = "6f1c1c64-0000-4000-8000-000000000009";
    private final CashFixture world = new CashFixture();

    private Outcome<RecordedMovement> income(String onBehalfOf, PaymentMedium medium, String key) {
        return world.recordSagaIncome()
                .execute(WORKER, TENANT, onBehalfOf, TODAY, medium, "Abono de la reserva", 25_000L, RESERVATION, key);
    }

    private Outcome<RecordedMovement> refund(String key) {
        return world.recordExecutedRefund().register(WORKER, TENANT, null, TODAY, PaymentMedium.TRANSFER,
                "Cancelacion con devolucion", 10_000L, RESERVATION, AUTHORIZATION, key);
    }

    @Test
    void aSagaIncomeIsStoredWithItsReservationAndItsEvent() {
        Outcome<RecordedMovement> done = income(null, PaymentMedium.TRANSFER, "key-income-0001");

        RecordedMovement movement = done.value();
        assertThat(done.replayed()).isFalse();
        assertThat(movement.movement().type()).isEqualTo(MovementType.INCOME);
        assertThat(movement.movement().amount().value()).isEqualTo(25_000L);
        assertThat(movement.reservationId()).isEqualTo(RESERVATION);
        assertThat(world.movements.findById(TENANT, movement.id())).contains(movement);
        assertThat(world.outbox.events()).extracting(event -> event.eventType()).contains("CashIncomeRecorded");
        assertThat(world.outbox.events().get(world.outbox.events().size() - 1).payload())
                .containsEntry("reservationId", RESERVATION);
    }

    @Test
    void theMovementIsAttributedToThePersonWhenTheSagaSaysSoAndToAStableIdOfTheServiceWhenNot() {
        RecordedMovement onBehalf = income(PERSON, PaymentMedium.TRANSFER, "key-income-0002").value();
        RecordedMovement alone = income(null, PaymentMedium.TRANSFER, "key-income-0003").value();
        RecordedMovement again = income(null, PaymentMedium.TRANSFER, "key-income-0004").value();

        assertThat(onBehalf.registeredBy()).isEqualTo(PERSON);
        assertThat(UUID.fromString(alone.registeredBy())).isNotNull();
        assertThat(alone.registeredBy()).isEqualTo(again.registeredBy());
        assertThat(alone.registeredBy()).isNotEqualTo(PERSON);
    }

    @Test
    void aRepeatedKeyAnswersTheStoredIncomeAsAReplay() {
        Outcome<RecordedMovement> first = income(null, PaymentMedium.TRANSFER, "key-income-0005");

        Outcome<RecordedMovement> again = income(null, PaymentMedium.TRANSFER, "key-income-0005");

        assertThat(again.replayed()).isTrue();
        assertThat(again.value()).isEqualTo(first.value());
        assertThat(world.movements.writes()).isEqualTo(1);
    }

    @Test
    void aCashIncomeNeedsTheOperationalOpening() {
        assertThatThrownBy(() -> income(null, PaymentMedium.CASH, "key-income-0006"))
                .isInstanceOf(CashRuleViolation.NoOpenCash.class);
        assertThat(world.movements.writes()).isZero();
    }

    @Test
    void aPersonCannotRecordASagaMovement() {
        assertThatThrownBy(() -> world.recordSagaIncome().execute(ADMIN, TENANT, null, TODAY, PaymentMedium.TRANSFER,
                        "Abono", 1_000L, RESERVATION, "key-income-0007"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThatThrownBy(() -> world.recordExecutedRefund().register(ADMIN, TENANT, null, TODAY,
                        PaymentMedium.TRANSFER, "Devolucion", 1_000L, RESERVATION, AUTHORIZATION, "key-refund-0007"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThat(world.movements.writes()).isZero();
    }

    @Test
    void aSagaRefundKeepsTheRealAuthorizationAndItsReservation() {
        Outcome<RecordedMovement> done = refund("key-refund-0001");

        RecordedMovement movement = done.value();
        assertThat(movement.movement().type()).isEqualTo(MovementType.REFUND);
        assertThat(movement.refund().reservationId()).isEqualTo(RESERVATION);
        assertThat(movement.refund().authorizationId()).isEqualTo(AUTHORIZATION);
        assertThat(world.outbox.events()).extracting(event -> event.eventType()).contains("RefundExecuted");
    }

    @Test
    void aRepeatedRefundKeyAnswersTheStoredRefundAsAReplay() {
        Outcome<RecordedMovement> first = refund("key-refund-0002");

        Outcome<RecordedMovement> again = refund("key-refund-0002");

        assertThat(again.replayed()).isTrue();
        assertThat(again.value()).isEqualTo(first.value());
    }
}
