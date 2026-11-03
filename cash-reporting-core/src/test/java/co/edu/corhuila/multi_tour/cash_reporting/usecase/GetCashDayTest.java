package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR_WITHOUT_PERMISSION;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.CUSTOMER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.MANAGER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TOMORROW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import org.junit.jupiter.api.Test;

class GetCashDayTest {

    private final CashFixture world = new CashFixture();

    private void seed(MovementType type, PaymentMedium medium, long cents) {
        world.movements.seed(TENANT, TODAY, new CashMovement(type, medium, Cents.of(cents)));
    }

    @Test
    void aDayWithoutCashIsTheNoOpeningView() {
        CashDay day = world.getCashDay().execute(ADMIN, YESTERDAY);

        assertThat(day.status()).isEqualTo(DayStatus.NO_OPENING);
        assertThat(day.session()).isNull();
        assertThat(day.date()).isEqualTo(YESTERDAY);
        assertThat(day.zone()).isEqualTo(BOGOTA);
        assertThat(day.pendingClosure()).isFalse();
        assertThat(day.closure()).isNull();
        assertThat(day.figures().incomeCents()).isEqualTo(Cents.ZERO);
        assertThat(day.figures().totalCents()).isEqualTo(Cents.ZERO);
        assertThat(day.figures().netTransfersCents()).isEqualTo(Cents.ZERO);
        assertThat(day.figures().expectedCashCents()).isNull();
        world.assertNothingWasWritten();
    }

    @Test
    void theCurrentDayIsToday() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, 1_000_000));
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000));
        seed(MovementType.INCOME, PaymentMedium.CASH, 10_000_000);
        seed(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 2_000_000);
        seed(MovementType.INCOME, PaymentMedium.TRANSFER, 5_000_000);

        CashDay day = world.getCashDay().current(ADMIN);

        assertThat(day.date()).isEqualTo(TODAY);
        assertThat(day.status()).isEqualTo(DayStatus.OPEN);
        assertThat(day.pendingClosure()).isFalse();
        assertThat(day.figures().openingBaseCents()).isEqualTo(Cents.of(30_000_000));
        assertThat(day.figures().incomeCents()).isEqualTo(Cents.of(15_000_000));
        assertThat(day.figures().expensesCents()).isEqualTo(Cents.of(2_000_000));
        assertThat(day.figures().totalCents()).isEqualTo(Cents.of(43_000_000));
        assertThat(day.figures().expectedCashCents()).isEqualTo(Cents.of(38_000_000));
        assertThat(day.figures().netTransfersCents()).isEqualTo(Cents.of(5_000_000));
        world.assertNothingWasWritten();
    }

    @Test
    void aPastOpenDayIsPendingClosureAndAClosedDayCarriesItsClosure() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, 1_000_000));
        assertThat(world.getCashDay().execute(ADMIN, YESTERDAY).pendingClosure()).isTrue();

        world.closeCashDay().execute(ADMIN, YESTERDAY, "key-close-0001");
        CashDay closed = world.getCashDay().execute(ADMIN, YESTERDAY);

        assertThat(closed.status()).isEqualTo(DayStatus.CLOSED);
        assertThat(closed.pendingClosure()).isFalse();
        assertThat(closed.closure().id()).isEqualTo("closure-1");
        assertThat(closed.closure().formalClosureOfPending()).isTrue();
    }

    @Test
    void aFutureDateIsRejected() {
        assertThatThrownBy(() -> world.getCashDay().execute(ADMIN, TOMORROW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTenantRoleReadsButACustomerCannot() {
        assertThat(world.getCashDay().current(MANAGER).status()).isEqualTo(DayStatus.NO_OPENING);
        assertThat(world.getCashDay().current(COLLABORATOR_WITHOUT_PERMISSION).status())
                .isEqualTo(DayStatus.NO_OPENING);
        assertThatThrownBy(() -> world.getCashDay().current(CUSTOMER)).isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThatThrownBy(() -> world.getCashDay().execute(CUSTOMER, TODAY))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
    }
}
