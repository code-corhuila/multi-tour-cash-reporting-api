package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import org.junit.jupiter.api.Test;

/** Job 8 closes only a cash created automatically whose day ended (AB-02); the rest stays pending (GAP-012). */
class AutoCloseCashDayTest {

    private final CashFixture world = new CashFixture();

    private void automaticDay(co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate date) {
        world.sessions.seed(CashSession.automatic(TENANT, date, BOGOTA));
    }

    @Test
    void anAutomaticCashWhoseDayEndedIsClosedAutomaticallyAndPublishesItsEvent() {
        automaticDay(YESTERDAY);

        CashClosure closure = world.autoCloseCashDay().execute(WORKER, TENANT, YESTERDAY);

        assertThat(closure.mode()).isEqualTo(ClosingMode.AUTOMATIC);
        assertThat(closure.origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(closure.closedAt()).isEqualTo(NOW);
        assertThat(closure.view().expectedCashCents()).as("no base, no expected cash").isNull();
        CashSession session = world.sessions.find(TENANT, YESTERDAY).orElseThrow();
        assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(session.closingMode()).isEqualTo(ClosingMode.AUTOMATIC);
        assertThat(world.outbox.events()).hasSize(1);
        assertThat(world.outbox.events().get(0).eventType()).isEqualTo("CashSessionAutoClosed");
        assertThat(world.outbox.events().get(0).tenantId()).isEqualTo(TENANT);
        assertThat(world.alerts.raised()).isEmpty();
    }

    @Test
    void anAutomaticCashKeepsItsTransfersInTheClosure() {
        automaticDay(YESTERDAY);
        world.movements.insert(TENANT, new RecordedMovement("m-1", YESTERDAY,
                new CashMovement(MovementType.INCOME, PaymentMedium.TRANSFER, Cents.of(5_000)), "Abono", false, NOW,
                "admin-1", null));

        CashClosure closure = world.autoCloseCashDay().execute(WORKER, TENANT, YESTERDAY);

        assertThat(closure.view().incomeCents().value()).isEqualTo(5_000);
    }

    @Test
    void aRepeatedCallAnswersTheStoredClosureAndWritesNothingMore() {
        automaticDay(YESTERDAY);
        CashClosure first = world.autoCloseCashDay().execute(WORKER, TENANT, YESTERDAY);

        CashClosure again = world.autoCloseCashDay().execute(WORKER, TENANT, YESTERDAY);

        assertThat(again).isEqualTo(first);
        assertThat(world.closures.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).hasSize(1);
    }

    @Test
    void aDayThatHasNotEndedInItsOwnZoneIsRefused() {
        automaticDay(TODAY);

        assertThatThrownBy(() -> world.autoCloseCashDay().execute(WORKER, TENANT, TODAY))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);

        assertThat(world.sessions.find(TENANT, TODAY).orElseThrow().status()).isEqualTo(SessionStatus.OPEN);
        assertThat(world.closures.writes()).isZero();
        assertThat(world.outbox.events()).isEmpty();
    }

    @Test
    void aCashWithOperationalOpeningIsNeverClosedAutomaticallyAndRaisesTheAlert() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, 30_000_000L));

        assertThatThrownBy(() -> world.autoCloseCashDay().execute(WORKER, TENANT, YESTERDAY))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);

        assertThat(world.sessions.find(TENANT, YESTERDAY).orElseThrow().status()).isEqualTo(SessionStatus.OPEN);
        assertThat(world.closures.writes()).isZero();
        assertThat(world.outbox.events()).isEmpty();
        assertThat(world.alerts.raised()).hasSize(1);
        assertThat(world.alerts.raised().get(0).tenantId()).isEqualTo(TENANT);
        assertThat(world.alerts.raised().get(0).date()).isEqualTo(YESTERDAY);
    }

    @Test
    void aDateWithoutCashIsNotFound() {
        assertThatThrownBy(() -> world.autoCloseCashDay().execute(WORKER, TENANT, YESTERDAY))
                .isInstanceOf(CashRuleViolation.NotFound.class);
    }

    @Test
    void aCashClosedFormallyIsNotClosedAgain() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L));
        world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");

        assertThatThrownBy(() -> world.autoCloseCashDay().execute(WORKER, TENANT, TODAY))
                .isInstanceOf(CashRuleViolation.AlreadyClosed.class);
    }

    @Test
    void onlyAServiceMayCloseAutomatically() {
        automaticDay(YESTERDAY);

        assertThatThrownBy(() -> world.autoCloseCashDay().execute(ADMIN, TENANT, YESTERDAY))
                .isInstanceOf(CashRuleViolation.Forbidden.class);

        assertThat(world.closures.writes()).isZero();
    }
}
