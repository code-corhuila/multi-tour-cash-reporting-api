package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import org.junit.jupiter.api.Test;

class RegisterCashCorrectionTest {

    private static final long BASE = 30_000_000L;
    private static final String JUSTIFICATION = "Se corrige la descripcion de un gasto sin cambiar valores";

    private final CashFixture world = new CashFixture();

    /** A cash of today with its operational opening, already closed by the Administrator (closure-1). */
    private CashClosure seedClosedDay() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));
        return world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");
    }

    @Test
    void aCorrectionNeedsItsJustificationAndKeepsWhoAndWhen() {
        seedClosedDay();
        RegisterCashCorrection correct = world.registerCashCorrection();

        assertThatThrownBy(() -> correct.execute(ADMIN, TODAY, "  ", "key-corr-0001"))
                .isInstanceOf(CashRuleViolation.ReasonRequired.class);
        assertThatThrownBy(() -> correct.execute(ADMIN, TODAY, null, "key-corr-0001"))
                .isInstanceOf(CashRuleViolation.ReasonRequired.class);
        assertThat(world.corrections.writes()).isZero();
        assertThat(world.keys.find(TENANT, "key-corr-0001")).isEmpty();

        CashCorrection correction = correct.execute(ADMIN, TODAY, "  " + JUSTIFICATION + " ", "key-corr-0001");

        assertThat(correction.id()).isEqualTo("correction-2");
        assertThat(correction.tenantId()).isEqualTo(TENANT);
        assertThat(correction.date()).isEqualTo(TODAY);
        assertThat(correction.justification()).isEqualTo(JUSTIFICATION);
        assertThat(correction.registeredBy()).isEqualTo("admin-1");
        assertThat(correction.registeredAt()).isEqualTo(NOW);
        assertThat(correction.closureId()).isEqualTo("closure-1");
        assertThat(world.corrections.rows()).containsExactly(correction);
        assertThat(world.keys.find(TENANT, "key-corr-0001")).isPresent();
        // No event exists for a correction (events.md): only the closing one was appended.
        assertThat(world.outbox.events()).hasSize(1);
        assertThat(world.unitOfWork.commits()).isEqualTo(2);
    }

    @Test
    void aRepeatedCorrectionKeyReturnsTheStoredCorrection() {
        seedClosedDay();
        RegisterCashCorrection correct = world.registerCashCorrection();
        CashCorrection first = correct.execute(ADMIN, TODAY, JUSTIFICATION, "key-corr-0001");

        CashCorrection again = correct.execute(ADMIN, TODAY, JUSTIFICATION, "key-corr-0001");

        assertThat(again).isEqualTo(first);
        assertThat(world.corrections.writes()).isEqualTo(1);
        assertThat(world.unitOfWork.commits()).isEqualTo(2);
    }

    @Test
    void onlyTheAdministratorRegistersACorrection() {
        seedClosedDay();

        assertThatThrownBy(() ->
                        world.registerCashCorrection().execute(COLLABORATOR, TODAY, JUSTIFICATION, "key-corr-0001"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);

        assertThat(world.corrections.writes()).isZero();
    }

    @Test
    void aCorrectionNeverChangesTheClosure() {
        CashClosure closure = seedClosedDay();
        CashSession closedSession = world.sessions.find(TENANT, TODAY).orElseThrow();
        int sessionWrites = world.sessions.writes();

        world.registerCashCorrection().execute(ADMIN, TODAY, JUSTIFICATION, "key-corr-0001");

        assertThat(world.closures.all(TENANT, TODAY)).containsExactly(closure);
        assertThat(world.closures.writes()).isEqualTo(1);
        assertThat(world.sessions.writes()).isEqualTo(sessionWrites);
        assertThat(world.sessions.find(TENANT, TODAY)).contains(closedSession);
        assertThat(closedSession.status()).isEqualTo(SessionStatus.CLOSED);
        assertThat(world.reopenings.writes()).isZero();
    }

    @Test
    void aCorrectionOnAnOpenDayIsAnInvalidTransition() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        assertThatThrownBy(() -> world.registerCashCorrection().execute(ADMIN, TODAY, JUSTIFICATION, "key-corr-0001"))
                .isInstanceOf(CashRuleViolation.InvalidTransition.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aCorrectionOnADateWithoutCashIsNotFound() {
        assertThatThrownBy(() -> world.registerCashCorrection().execute(ADMIN, TODAY, JUSTIFICATION, "key-corr-0001"))
                .isInstanceOf(CashRuleViolation.NotFound.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aCorrectionOnAnAutomaticClosureIsRefused() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE).close(ClosingMode.AUTOMATIC));

        assertThatThrownBy(() -> world.registerCashCorrection().execute(ADMIN, TODAY, JUSTIFICATION, "key-corr-0001"))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);

        world.assertNothingWasWritten();
    }
}
