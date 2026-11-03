package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR_WITHOUT_PERMISSION;
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
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegisterCashMovementTest {

    private static final long BASE = 30_000_000L;

    private final CashFixture world = new CashFixture();

    private RecordedMovement manual(
            BusinessDate date, MovementType type, PaymentMedium medium, long cents, String key) {
        return world.registerCashMovement()
                .execute(ADMIN, MovementSource.MANUAL, date, type, medium, "Compra operativa", cents, key);
    }

    private DayView viewOf(BusinessDate date) {
        CashSession session = world.sessions.find(TENANT, date).orElseThrow();
        Cents base = session.opening() == null ? null : session.opening().base();
        return DayTotals.compute(base, world.movements.findByDate(TENANT, date));
    }

    @Test
    void cashWithoutOpeningIsRefusedNoOpenCash() {
        assertThatThrownBy(() -> manual(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 2_500_000L, "key-nocash-001"))
                .isInstanceOf(CashRuleViolation.NoOpenCash.class);

        world.assertNothingWasWritten();
    }

    @Test
    void cashOnAnAutomaticDayIsRefusedNoOpenCash() {
        world.sessions.seed(CashSession.automatic(TENANT, TODAY, BOGOTA));

        assertThatThrownBy(() -> manual(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 2_500_000L, "key-auto-0001"))
                .isInstanceOf(CashRuleViolation.NoOpenCash.class);

        world.assertNothingWasWritten();
    }

    @Test
    void cashOnAClosedDayIsRefusedAlreadyClosed() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE).close(ClosingMode.MANUAL));

        assertThatThrownBy(() -> manual(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 2_500_000L, "key-closed-001"))
                .isInstanceOf(CashRuleViolation.AlreadyClosed.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aTransferOnADayWithoutCashCreatesItAutomatically() {
        RecordedMovement stored = manual(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 5_000_000L, "key-transfer-01");

        CashSession session = world.sessions.find(TENANT, TODAY).orElseThrow();
        assertThat(session.origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(session.opening()).isNull();
        assertThat(session.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.zone()).isEqualTo(BOGOTA);
        assertThat(world.sessions.writes()).isEqualTo(1);
        assertThat(stored.afterClosing()).isFalse();
        assertThat(world.outbox.events())
                .extracting(event -> event.eventType())
                .containsExactly("CashSessionAutoCreated", "CashIncomeRecorded");
        assertThat(world.outbox.events().get(0).payload()).containsEntry("businessDate", "2026-10-14");
        assertThat(world.keys.find(TENANT, "key-transfer-01")).isPresent();
    }

    @Test
    void anAutomaticCashHasNoExpectedCash() {
        manual(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 5_000_000L, "key-transfer-02");

        DayView view = viewOf(TODAY);

        assertThat(view.openingBaseCents()).isNull();
        assertThat(view.expectedCashCents()).isNull();
        assertThat(view.totalCents()).isEqualTo(Cents.of(5_000_000L));
    }

    @Test
    void aTransferAfterTheClosingIsStoredAfterClosingWithoutReopening() {
        CashSession closed = CashFixture.openedOn(TODAY, BASE).close(ClosingMode.MANUAL);
        world.sessions.seed(closed);

        RecordedMovement stored = manual(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 2_000_000L, "key-after-0001");

        assertThat(stored.afterClosing()).isTrue();
        assertThat(world.sessions.find(TENANT, TODAY)).contains(closed);
        assertThat(world.sessions.writes()).isZero();
        assertThat(world.outbox.events())
                .extracting(event -> event.eventType())
                .containsExactly("CashIncomeRecorded");
    }

    @Test
    void aTransferAfterTheClosingKeepsTheExpectedCash() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE).close(ClosingMode.MANUAL));
        world.movements.seed(
                TENANT, TODAY, new CashMovement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, Cents.of(5_000_000L)));
        assertThat(viewOf(TODAY).expectedCashCents()).isEqualTo(Cents.of(25_000_000L));

        manual(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 2_000_000L, "key-after-0002");

        DayView after = viewOf(TODAY);
        assertThat(after.expectedCashCents()).isEqualTo(Cents.of(25_000_000L));
        assertThat(after.totalCents()).isEqualTo(Cents.of(27_000_000L));
        assertThat(after.netTransfersCents()).isEqualTo(Cents.of(2_000_000L));
    }

    @Test
    void aTransferAnotherDayBelongsToThatDay() {
        CashSession yesterday = CashFixture.openedOn(YESTERDAY, 10_000_000L).close(ClosingMode.MANUAL);
        world.sessions.seed(yesterday);

        RecordedMovement stored = manual(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 3_000_000L, "key-today-0001");

        assertThat(stored.date()).isEqualTo(TODAY);
        assertThat(stored.afterClosing()).isFalse();
        assertThat(world.sessions.find(TENANT, TODAY).orElseThrow().origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(world.sessions.find(TENANT, YESTERDAY)).contains(yesterday);
        assertThat(world.movements.findByDate(TENANT, YESTERDAY)).isEmpty();
        assertThat(world.movements.findByDate(TENANT, TODAY)).hasSize(1);
    }

    @Test
    void aMovementOnAnotherDateIsRefused() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, BASE).close(ClosingMode.MANUAL));

        assertThatThrownBy(() -> manual(YESTERDAY, MovementType.INCOME, PaymentMedium.TRANSFER, 1_000_000L, "key-other-0001"))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);
        assertThatThrownBy(() -> manual(BusinessDate.of(YESTERDAY.value().minusDays(5)), MovementType.INCOME, PaymentMedium.TRANSFER, 1_000_000L, "key-other-0002"))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);
        assertThatThrownBy(() -> manual(TOMORROW, MovementType.INCOME, PaymentMedium.TRANSFER, 1_000_000L, "key-other-0003"))
                .isInstanceOf(IllegalArgumentException.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aPendingCashAcceptsNoMovement() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, BASE));

        assertThatThrownBy(() -> manual(YESTERDAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 1_000_000L, "key-pending-001"))
                .isInstanceOf(CashRuleViolation.PendingClosure.class);
        assertThatThrownBy(() -> manual(YESTERDAY, MovementType.INCOME, PaymentMedium.TRANSFER, 1_000_000L, "key-pending-002"))
                .isInstanceOf(CashRuleViolation.PendingClosure.class);

        world.assertNothingWasWritten();
    }

    @Test
    void noOperationStoresAPendingTransfer() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        manual(TODAY, MovementType.INCOME, PaymentMedium.TRANSFER, 4_000_000L, "key-validated-1");

        // a stored transfer is a validated one: it counts at once and the stored row has no pending state
        DayView view = viewOf(TODAY);
        assertThat(view.totalCents()).isEqualTo(Cents.of(34_000_000L));
        assertThat(view.expectedCashCents()).isEqualTo(Cents.of(BASE));
        assertThat(Arrays.stream(RecordedMovement.class.getRecordComponents()).map(c -> c.getName()))
                .noneMatch(name -> name.toLowerCase().contains("status") || name.toLowerCase().contains("pending"));
    }

    @Test
    void aCollaboratorWithPermissionRegistersAnExpense() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        RecordedMovement stored = world.registerCashMovement()
                .execute(COLLABORATOR, MovementSource.MANUAL, TODAY, MovementType.OPERATIONAL_EXPENSE,
                        PaymentMedium.CASH, "Compra operativa", 2_500_000L, "key-collab-0001");

        assertThat(stored.registeredBy()).isEqualTo("collab-1");
        assertThat(stored.registeredAt()).isEqualTo(NOW);
        assertThat(stored.concept()).isEqualTo("Compra operativa");
        assertThat(stored.movement())
                .isEqualTo(new CashMovement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, Cents.of(2_500_000L)));
        assertThat(world.movements.findById(TENANT, stored.id())).contains(stored);
        assertThat(world.outbox.events())
                .extracting(event -> event.eventType())
                .containsExactly("OperationalExpenseRecorded");
    }

    @Test
    void aCollaboratorWithoutPermissionIsForbidden() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        assertThatThrownBy(() -> world.registerCashMovement()
                        .execute(COLLABORATOR_WITHOUT_PERMISSION, MovementSource.MANUAL, TODAY,
                                MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, "Compra operativa", 2_500_000L,
                                "key-forbid-0001"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aRepeatedKeyReturnsTheStoredResultAndWritesNothing() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));
        RecordedMovement first = manual(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 2_500_000L, "key-repeat-0001");

        RecordedMovement again = manual(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 9_900_000L, "key-repeat-0001");

        assertThat(again).isEqualTo(first);
        assertThat(world.movements.writes()).isEqualTo(1);
        assertThat(world.keys.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).hasSize(1);
        assertThat(world.unitOfWork.commits()).isEqualTo(1);
    }

    @Test
    void aKeyOfAnotherOperationIsRefused() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));
        world.keys.put(TENANT, "key-foreign-001", "CASH_DAY", "2026-10-14");

        assertThatThrownBy(() -> manual(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 2_500_000L, "key-foreign-001"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(world.movements.writes()).isZero();
        assertThat(world.outbox.events()).isEmpty();
    }

    @Test
    void theEventMatchesTheMovementType() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        RecordedMovement income = manual(TODAY, MovementType.INCOME, PaymentMedium.CASH, 1_000_000L, "key-event-0001");
        manual(TODAY, MovementType.OPERATIONAL_PAYMENT, PaymentMedium.CASH, 1_000_000L, "key-event-0002");
        manual(TODAY, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 1_000_000L, "key-event-0003");

        assertThat(world.outbox.events())
                .extracting(event -> event.eventType())
                .containsExactly("CashIncomeRecorded", "OperationalPaymentRecorded", "OperationalExpenseRecorded");
        var first = world.outbox.events().get(0);
        assertThat(first.tenantId()).isEqualTo(TENANT);
        assertThat(first.aggregateId()).isEqualTo(income.id());
        assertThat(first.payload())
                .containsEntry("movementId", income.id())
                .containsEntry("businessDate", "2026-10-14")
                .containsEntry("movementType", "INCOME")
                .containsEntry("paymentMedium", "CASH")
                .containsEntry("amountCents", 1_000_000L)
                .containsEntry("afterClosing", false)
                .doesNotContainKey("concept");
    }

    @Test
    void theEventsAndTheKeyShareOneCommit() {
        List<Boolean> insideTheUnit = new ArrayList<>();
        OutboxWriter outbox = (tenantId, type, aggregateId, payload) -> {
            insideTheUnit.add(world.unitOfWork.active());
            world.outbox.append(tenantId, type, aggregateId, payload);
        };
        IdempotencyStore keys = new IdempotencyStore() {
            @Override
            public Optional<Stored> find(String tenantId, String key) {
                return world.keys.find(tenantId, key);
            }

            @Override
            public void put(String tenantId, String key, String resourceType, String resourceId) {
                insideTheUnit.add(world.unitOfWork.active());
                world.keys.put(tenantId, key, resourceType, resourceId);
            }
        };
        var useCase = new RegisterCashMovement(world.permissions, world.sessions, world.movements, keys, outbox,
                world.calendar, world.clock, () -> "movement-1", world.unitOfWork);

        useCase.execute(ADMIN, MovementSource.MANUAL, TODAY, MovementType.INCOME, PaymentMedium.TRANSFER,
                "Abono por transferencia", 5_000_000L, "key-one-unit-01");

        assertThat(world.outbox.events()).hasSize(2);
        assertThat(insideTheUnit).containsExactly(true, true, true);
        assertThat(world.unitOfWork.commits()).isEqualTo(1);
        assertThat(world.unitOfWork.rollbacks()).isZero();
    }

    @Test
    void aClientCannotRegisterARefundByHand() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        assertThatThrownBy(() -> manual(TODAY, MovementType.REFUND, PaymentMedium.CASH, 1_000_000L, "key-refund-0001"))
                .isInstanceOf(IllegalArgumentException.class);

        world.assertNothingWasWritten();
    }

    @Test
    void theSagaRegistersAValidatedIncomeWithoutThePermissionTable() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        RecordedMovement stored = world.registerCashMovement()
                .execute(COLLABORATOR_WITHOUT_PERMISSION, MovementSource.SAGA, TODAY, MovementType.INCOME,
                        PaymentMedium.CASH, "Pago de reserva", 1_000_000L, "key-saga-00001");

        assertThat(stored.movement().type()).isEqualTo(MovementType.INCOME);
        assertThat(world.outbox.events()).extracting(event -> event.eventType()).containsExactly("CashIncomeRecorded");
    }

    @Test
    void theRefundRowShowsItsReservation() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        RecordedMovement refund = world.recordExecutedRefund()
                .execute(ADMIN, TODAY, PaymentMedium.TRANSFER, "reservation-1902",
                        "Devolucion reserva #RES-1902", 15_000_000L, "key-refund-saga1");

        assertThat(refund.movement().type()).isEqualTo(MovementType.REFUND);
        assertThat(refund.refund()).isNotNull();
        assertThat(refund.refund().reservationId()).isEqualTo("reservation-1902");
        assertThat(world.movements.findById(TENANT, refund.id())).contains(refund);
        assertThat(world.outbox.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("RefundExecuted");
            assertThat(event.payload()).containsEntry("reservationId", "reservation-1902");
        });
        DayView view = viewOf(TODAY);
        assertThat(view.refundsCents()).isEqualTo(Cents.of(15_000_000L));
        assertThat(view.expensesCents()).isEqualTo(Cents.ZERO);
        assertThat(view.totalCents()).isEqualTo(Cents.of(15_000_000L));
        assertThat(view.expectedCashCents()).isEqualTo(Cents.of(BASE));
    }
}
