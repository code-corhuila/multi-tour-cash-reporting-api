package co.edu.corhuila.multi_tour.cash_reporting.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CashSessionTest {

    private static final String TENANT = "tenant-1";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final BusinessDate DAY = BusinessDate.of(LocalDate.of(2026, 10, 14));
    private static final BusinessDate NEXT_DAY = BusinessDate.of(LocalDate.of(2026, 10, 15));
    private static final CashSession.Opening OPENING =
            new CashSession.Opening(Cents.of(30_000_000L), Instant.parse("2026-10-14T13:00:00Z"), "user-1");

    private static CashSession openCash() {
        return CashSession.openNew(TENANT, DAY, BOGOTA, OPENING, DAY);
    }

    private static CashMovement cashIncome() {
        return new CashMovement(MovementType.INCOME, PaymentMedium.CASH, Cents.amount(1000));
    }

    private static CashMovement transfer() {
        return new CashMovement(MovementType.INCOME, PaymentMedium.TRANSFER, Cents.amount(1000));
    }

    @Test
    void anOpenCashOfAPastDateIsPending() {
        assertThat(openCash().isPending(DAY)).isFalse();
        assertThat(openCash().isPending(NEXT_DAY)).isTrue();
    }

    @Test
    void aClosedCashIsNeverPending() {
        CashSession closed = openCash().close(ClosingMode.MANUAL);

        assertThat(closed.isPending(NEXT_DAY)).isFalse();
        assertThat(closed.withStoredPendingMark(true).isPending(NEXT_DAY)).isFalse();
    }

    @Test
    void anAutomaticCashWithoutOpeningIsNeverPending() {
        CashSession automatic = CashSession.automatic(TENANT, DAY, BOGOTA);

        assertThat(automatic.isPending(NEXT_DAY)).isFalse();
        assertThat(automatic.hasOperationalOpening()).isFalse();
    }

    @Test
    void aStoredPendingMarkIsHonoredNextToTheDerivedState() {
        assertThat(openCash().withStoredPendingMark(true).isPending(DAY)).isTrue();
        assertThat(openCash().withStoredPendingMark(false).isPending(DAY)).isFalse();
    }

    @Test
    void anOpeningOfAnAutomaticCashKeepsItsOrigin() {
        CashSession automatic = CashSession.automatic(TENANT, DAY, BOGOTA);

        CashSession opened = automatic.open(OPENING, DAY);

        assertThat(opened.origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(opened.hasOperationalOpening()).isTrue();
        assertThat(opened.opening().base()).isEqualTo(Cents.of(30_000_000L));
        assertThat(openCash().origin()).isEqualTo(Origin.OPERATIONAL_OPENING);
    }

    @Test
    void aCashWithOpeningCannotBeOpenedAgainNorOnAnotherDay() {
        assertThatThrownBy(() -> openCash().open(OPENING, DAY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CashSession.openNew(TENANT, DAY, BOGOTA, OPENING, NEXT_DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aClosedCashIsClosedOnceAndAdjustsNoBase() {
        CashSession closed = openCash().close(ClosingMode.MANUAL);

        assertThat(closed.status()).isEqualTo(SessionStatus.CLOSED);
        assertThatThrownBy(() -> closed.close(ClosingMode.MANUAL)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> closed.adjustBase(Cents.of(1))).isInstanceOf(IllegalStateException.class);
        assertThat(openCash().adjustBase(Cents.of(5)).opening().base()).isEqualTo(Cents.of(5));
    }

    @Test
    void closingClearsTheStoredPendingMark() {
        CashSession closed = openCash().withStoredPendingMark(true).close(ClosingMode.MANUAL);

        assertThat(closed.storedPendingMark()).isFalse();
    }

    @Test
    void aReopenedCashIsOpenAgainExceptWhenItWasClosedAutomatically() {
        CashSession reopened = openCash().close(ClosingMode.MANUAL).reopen();

        assertThat(reopened.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(reopened.closingMode()).isNull();
        assertThatThrownBy(() -> openCash().reopen()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> openCash().close(ClosingMode.AUTOMATIC).reopen())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPendingCashAcceptsNoMovement() {
        assertThat(openCash().accepts(cashIncome(), NEXT_DAY)).isEqualTo(Acceptance.PENDING_CLOSURE);
        assertThat(openCash().accepts(transfer(), NEXT_DAY)).isEqualTo(Acceptance.PENDING_CLOSURE);
    }

    @Test
    void onlyTodayAcceptsMovements() {
        CashSession automatic = CashSession.automatic(TENANT, DAY, BOGOTA);

        assertThat(automatic.accepts(transfer(), NEXT_DAY)).isEqualTo(Acceptance.NOT_TODAY);
        assertThat(openCash().close(ClosingMode.MANUAL).accepts(transfer(), NEXT_DAY))
                .isEqualTo(Acceptance.NOT_TODAY);
    }

    @Test
    void cashNeedsTheOperationalOpeningAndAnOpenDayButATransferDoesNot() {
        CashSession automatic = CashSession.automatic(TENANT, DAY, BOGOTA);
        CashSession closed = openCash().close(ClosingMode.MANUAL);

        assertThat(automatic.accepts(cashIncome(), DAY)).isEqualTo(Acceptance.NO_OPEN_CASH);
        assertThat(closed.accepts(cashIncome(), DAY)).isEqualTo(Acceptance.ALREADY_CLOSED);
        assertThat(openCash().accepts(cashIncome(), DAY)).isEqualTo(Acceptance.ACCEPTED);
        assertThat(automatic.accepts(transfer(), DAY)).isEqualTo(Acceptance.ACCEPTED);
        assertThat(closed.accepts(transfer(), DAY)).isEqualTo(Acceptance.ACCEPTED_AFTER_CLOSING);
    }
}
