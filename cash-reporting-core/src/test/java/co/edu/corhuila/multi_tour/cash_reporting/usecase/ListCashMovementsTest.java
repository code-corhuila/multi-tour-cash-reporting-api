package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.CUSTOMER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.MANAGER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TOMORROW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ListCashMovementsTest {

    private final CashFixture world = new CashFixture();

    private static RecordedMovement row(
            String id, BusinessDate date, MovementType type, PaymentMedium medium, long cents, long hoursAgo) {
        RefundDetail refund = type == MovementType.REFUND ? new RefundDetail("res-1902") : null;
        return new RecordedMovement(
                id,
                date,
                new CashMovement(type, medium, Cents.of(cents)),
                "concept " + id,
                false,
                NOW.minus(Duration.ofHours(hoursAgo)),
                "admin-1",
                refund);
    }

    private void seed(String tenant, RecordedMovement movement) {
        world.movements.seedRecorded(tenant, movement);
    }

    @Test
    void movementsAreListedMostRecentFirst() {
        seed(TENANT, row("m-old", TODAY, MovementType.INCOME, PaymentMedium.CASH, 100, 3));
        seed(TENANT, row("m-new", TODAY, MovementType.INCOME, PaymentMedium.CASH, 200, 1));
        seed(TENANT, row("m-mid", TODAY, MovementType.INCOME, PaymentMedium.CASH, 300, 2));
        seed(TENANT, row("m-yesterday", YESTERDAY, MovementType.INCOME, PaymentMedium.CASH, 1, 0));
        seed("tenant-2", row("m-other", TODAY, MovementType.INCOME, PaymentMedium.CASH, 1, 0));

        Page<RecordedMovement> first = world.listCashMovements().execute(ADMIN, TODAY, PageRequest.of(1, 2));
        Page<RecordedMovement> second = world.listCashMovements().execute(ADMIN, TODAY, PageRequest.of(2, 2));

        assertThat(first.items()).extracting(RecordedMovement::id).containsExactly("m-new", "m-mid");
        assertThat(second.items()).extracting(RecordedMovement::id).containsExactly("m-old");
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        world.assertNothingWasWritten();
    }

    @Test
    void theRefundRowShowsItsReservation() {
        seed(TENANT, row("m-refund", TODAY, MovementType.REFUND, PaymentMedium.CASH, 15_000_000, 1));
        seed(TENANT, row("m-income", TODAY, MovementType.INCOME, PaymentMedium.CASH, 100, 2));

        Page<RecordedMovement> page = world.listCashMovements().execute(MANAGER, TODAY, PageRequest.of(null, null));

        assertThat(page.items().get(0).movement().type()).isEqualTo(MovementType.REFUND);
        assertThat(page.items().get(0).refund().reservationId()).isEqualTo("res-1902");
        assertThat(page.items().get(1).refund()).isNull();
    }

    @Test
    void aPendingTransferIsNeverARow() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000));
        // Nothing stores a pending transfer: before the validated income arrives, the day lists no row at all.
        assertThat(world.listCashMovements().execute(ADMIN, TODAY, PageRequest.of(null, null)).items())
                .isEmpty();

        world.registerCashMovement()
                .execute(
                        ADMIN,
                        MovementSource.MANUAL,
                        TODAY,
                        MovementType.INCOME,
                        PaymentMedium.TRANSFER,
                        "Transferencia validada",
                        5_000_000L,
                        "key-move-0001");

        Page<RecordedMovement> page = world.listCashMovements().execute(ADMIN, TODAY, PageRequest.of(null, null));
        assertThat(page.items()).singleElement().satisfies(listed -> {
            assertThat(listed.movement().medium()).isEqualTo(PaymentMedium.TRANSFER);
            assertThat(listed.movement().amount()).isEqualTo(Cents.of(5_000_000));
        });
        // Once it is a row, it still never changes the expected cash.
        assertThat(world.getCashDay().current(ADMIN).figures().expectedCashCents())
                .isEqualTo(Cents.of(30_000_000));
    }

    @Test
    void aDayWithoutCashListsNothingAndAFutureDateIsRejected() {
        assertThat(world.listCashMovements().execute(ADMIN, YESTERDAY, PageRequest.of(null, null)).items())
                .isEmpty();
        assertThatThrownBy(() -> world.listCashMovements().execute(ADMIN, TOMORROW, PageRequest.of(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> world.listCashMovements().execute(CUSTOMER, TODAY, PageRequest.of(null, null)))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
    }
}
