package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import org.junit.jupiter.api.Test;

/** The read the saga register-payment makes after an unknown outcome of its step 1 (AC-WF2-11): one income per payment and tenant. */
class GetCashIncomeOfPaymentTest {

    private static final String RESERVATION = "6f1c1c64-0000-4000-8000-000000000001";
    private static final String PAYMENT = "6f1c1c64-0000-4000-8000-0000000000c1";
    private static final String OTHER_TENANT = "6f1c1c64-0000-4000-8000-0000000000ff";
    private final CashFixture world = new CashFixture();

    private RecordedMovement income() {
        return world.recordSagaIncome().execute(WORKER, TENANT, null, TODAY, PaymentMedium.TRANSFER,
                "Abono de la reserva", 25_000L, RESERVATION, PAYMENT, "key-income-of-payment-1").value();
    }

    @Test
    void theIncomeOfThePaymentIsReadForTheTenantOfThePath() {
        RecordedMovement recorded = income();

        assertThat(world.getCashIncomeOfPayment().execute(WORKER, TENANT, PAYMENT)).isEqualTo(recorded);
        assertThat(world.movements.writes()).isEqualTo(1);
    }

    @Test
    void aPaymentWithoutIncomeAndAnIncomeOfAnotherTenantAreNotFound() {
        income();

        assertThatThrownBy(() -> world.getCashIncomeOfPayment().execute(WORKER, TENANT, RESERVATION))
                .isInstanceOf(CashRuleViolation.NotFound.class);
        assertThatThrownBy(() -> world.getCashIncomeOfPayment().execute(WORKER, OTHER_TENANT, PAYMENT))
                .isInstanceOf(CashRuleViolation.NotFound.class);
    }

    @Test
    void onlyAServiceMayRead() {
        income();

        assertThatThrownBy(() -> world.getCashIncomeOfPayment().execute(ADMIN, TENANT, PAYMENT))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
    }
}
