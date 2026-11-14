package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import org.junit.jupiter.api.Test;

/** HU-WFL-002, audit of 2026-11-13: a payment becomes one income, whatever the Idempotency-Key of the saga that asks. */
class PaymentIncomeOnceTest {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String PAYMENT = "6f1c1c64-0000-4000-8000-0000000000b1";
    private static final String OTHER_TENANT = "6f1c1c64-0000-4000-8000-0000000000c9";
    private final CashFixture world = new CashFixture();

    private Outcome<RecordedMovement> income(String tenant, String payment, String key) {
        return world.recordSagaIncome().execute(WORKER, tenant, null, TODAY, PaymentMedium.TRANSFER, "Abono de la reserva",
                25_000L, RESERVATION, payment, key);
    }

    @Test
    void theIncomeKeepsTheReferenceOfItsPayment() {
        RecordedMovement stored = income(TENANT, PAYMENT, "key-income-0001").value();

        assertThat(stored.paymentReference()).isEqualTo(PAYMENT);
        assertThat(world.movements.findById(TENANT, stored.id())).contains(stored);
    }

    @Test
    void aSecondKeyOverTheSamePaymentRecordsNoSecondIncome() {
        income(TENANT, PAYMENT, "key-income-0001");

        assertThatThrownBy(() -> income(TENANT, PAYMENT, "key-income-0002"))
                .isInstanceOf(CashRuleViolation.PaymentAlreadyRecorded.class);
        assertThat(world.movements.writes()).isEqualTo(1);
    }

    @Test
    void theSameKeyOverTheSamePaymentIsStillAReplay() {
        RecordedMovement first = income(TENANT, PAYMENT, "key-income-0001").value();

        Outcome<RecordedMovement> again = income(TENANT, PAYMENT, "key-income-0001");

        assertThat(again.replayed()).isTrue();
        assertThat(again.value()).isEqualTo(first);
        assertThat(world.movements.writes()).isEqualTo(1);
    }

    @Test
    void aReversedIncomeKeepsItsPaymentTaken() {
        RecordedMovement first = income(TENANT, PAYMENT, "key-income-0001").value();
        world.reverseCashMovement().execute(WORKER, TENANT, null, first.id(), "Falla el registro del pago", "key-reverse-0001");

        assertThatThrownBy(() -> income(TENANT, PAYMENT, "key-income-0002"))
                .isInstanceOf(CashRuleViolation.PaymentAlreadyRecorded.class);
    }

    @Test
    void anotherTenantAndAnIncomeWithoutPaymentAreNotLimited() {
        income(TENANT, PAYMENT, "key-income-0001");

        assertThat(income(OTHER_TENANT, PAYMENT, "key-income-0002").replayed()).isFalse();
        assertThat(income(TENANT, null, "key-income-0003").replayed()).isFalse();
        assertThat(income(TENANT, null, "key-income-0004").replayed()).isFalse();
    }
}
