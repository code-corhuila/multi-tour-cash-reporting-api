package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR_WITHOUT_PERMISSION;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TOMORROW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import org.junit.jupiter.api.Test;

class CloseCashDayTest {

    private static final long BASE = 30_000_000L;

    private final CashFixture world = new CashFixture();

    /** The portal demo day: total 119000000, expected cash 5000000. */
    private void seedDemoDay() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));
        world.movements.seed(
                TENANT, TODAY, new CashMovement(MovementType.INCOME, PaymentMedium.TRANSFER, Cents.of(129_000_000L)));
        world.movements.seed(
                TENANT,
                TODAY,
                new CashMovement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, Cents.of(25_000_000L)));
        world.movements.seed(
                TENANT, TODAY, new CashMovement(MovementType.REFUND, PaymentMedium.TRANSFER, Cents.of(15_000_000L)));
    }

    @Test
    void closingStoresTheSnapshotOfTheDay() {
        seedDemoDay();

        CashClosure closure = world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");

        assertThat(closure.id()).isEqualTo("closure-1");
        assertThat(closure.date()).isEqualTo(TODAY);
        assertThat(closure.origin()).isEqualTo(Origin.OPERATIONAL_OPENING);
        assertThat(closure.mode()).isEqualTo(ClosingMode.MANUAL);
        assertThat(closure.closedBy()).isEqualTo("admin-1");
        assertThat(closure.closedAt()).isEqualTo(NOW);
        assertThat(closure.formalClosureOfPending()).isFalse();
        assertThat(closure.closingBalance()).isEqualTo(Cents.of(119_000_000L));
        assertThat(closure.view().expectedCashCents()).isEqualTo(Cents.of(5_000_000L));
        assertThat(world.closures.findLatest(TENANT, TODAY)).contains(closure);
        CashSession session = world.sessions.find(TENANT, TODAY).orElseThrow();
        assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(session.closingMode()).isEqualTo(ClosingMode.MANUAL);
        assertThat(world.outbox.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("CashSessionClosed");
            assertThat(event.tenantId()).isEqualTo(TENANT);
            assertThat(event.payload())
                    .containsEntry("businessDate", "2026-10-14")
                    .containsEntry("closureId", "closure-1")
                    .containsEntry("closingBalanceCents", 119_000_000L)
                    .containsEntry("formalClosureOfPending", false);
        });
        assertThat(world.keys.find(TENANT, "key-close-0001")).isPresent();
        assertThat(world.unitOfWork.commits()).isEqualTo(1);
    }

    @Test
    void aSecondClosingIsRefusedAlreadyClosed() {
        seedDemoDay();
        CloseCashDay close = world.closeCashDay();
        close.execute(ADMIN, TODAY, "key-close-0001");

        assertThatThrownBy(() -> close.execute(ADMIN, TODAY, "key-close-0002"))
                .isInstanceOf(CashRuleViolation.AlreadyClosed.class);

        assertThat(world.closures.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).hasSize(1);
        assertThat(world.keys.writes()).isEqualTo(1);
    }

    @Test
    void closingWithoutOpeningIsRefusedNoOpenCash() {
        assertThatThrownBy(() -> world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001"))
                .isInstanceOf(CashRuleViolation.NoOpenCash.class);
        world.assertNothingWasWritten();

        world.sessions.seed(CashSession.automatic(TENANT, TODAY, BOGOTA));

        assertThatThrownBy(() -> world.closeCashDay().execute(ADMIN, TODAY, "key-close-0002"))
                .isInstanceOf(CashRuleViolation.NoOpenCash.class);
        world.assertNothingWasWritten();
    }

    @Test
    void closingAFutureDateIsRejected() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        assertThatThrownBy(() -> world.closeCashDay().execute(ADMIN, TOMORROW, "key-close-0001"))
                .isInstanceOf(IllegalArgumentException.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aPendingCashIsClosedFormallyKeepingItsDate() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, BASE));
        world.movements.seed(
                TENANT,
                YESTERDAY,
                new CashMovement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, Cents.of(5_000_000L)));

        CashClosure closure = world.closeCashDay().execute(ADMIN, YESTERDAY, "key-close-0001");

        assertThat(closure.date()).isEqualTo(YESTERDAY);
        assertThat(closure.formalClosureOfPending()).isTrue();
        assertThat(closure.view().expectedCashCents()).isEqualTo(Cents.of(25_000_000L));
        assertThat(world.sessions.find(TENANT, YESTERDAY).orElseThrow().status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(world.sessions.find(TENANT, TODAY)).isEmpty();
        assertThat(world.outbox.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("CashSessionClosed");
            assertThat(event.payload())
                    .containsEntry("businessDate", "2026-10-13")
                    .containsEntry("formalClosureOfPending", true);
        });
    }

    @Test
    void theNextDayCashIsIndependentOfAPendingOne() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, BASE));

        world.openCashDay().execute(ADMIN, TODAY, 10_000_000L, "key-open-0001");
        CashClosure today = world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");

        assertThat(today.date()).isEqualTo(TODAY);
        assertThat(today.formalClosureOfPending()).isFalse();
        assertThat(today.closingBalance()).isEqualTo(Cents.of(10_000_000L));
        CashSession yesterday = world.sessions.find(TENANT, YESTERDAY).orElseThrow();
        assertThat(yesterday.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(yesterday.isPending(TODAY)).isTrue();
        assertThat(world.closures.findLatest(TENANT, YESTERDAY)).isEmpty();
    }

    @Test
    void aKeyOfAnotherOperationIsRefused() {
        seedDemoDay();
        world.keys.put(TENANT, "key-foreign-001", "CASH_MOVEMENT", "6f1c1c64-0000-4000-8000-000000000001");

        assertThatThrownBy(() -> world.closeCashDay().execute(ADMIN, TODAY, "key-foreign-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");

        assertThat(world.closures.writes()).isZero();
        assertThat(world.outbox.events()).isEmpty();
    }

    @Test
    void aRepeatedClosingKeyReturnsTheStoredClosure() {
        seedDemoDay();
        CloseCashDay close = world.closeCashDay();
        CashClosure first = close.execute(ADMIN, TODAY, "key-close-0001");

        CashClosure again = close.execute(ADMIN, TODAY, "key-close-0001");

        assertThat(again).isEqualTo(first);
        assertThat(world.closures.writes()).isEqualTo(1);
        assertThat(world.sessions.writes()).isEqualTo(1);
        assertThat(world.keys.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).hasSize(1);
        assertThat(world.unitOfWork.commits()).isEqualTo(1);
    }

    @Test
    void closingClearsTheStoredPendingMark() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE).withStoredPendingMark(true));

        world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");

        CashSession session = world.sessions.find(TENANT, TODAY).orElseThrow();
        assertThat(session.storedPendingMark()).isFalse();
        assertThat(session.isPending(TODAY)).isFalse();
        assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    void aCollaboratorWithoutPermissionCannotClose() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        assertThatThrownBy(() -> world.closeCashDay().execute(COLLABORATOR_WITHOUT_PERMISSION, TODAY, "key-close-0001"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);

        world.assertNothingWasWritten();
    }
}
