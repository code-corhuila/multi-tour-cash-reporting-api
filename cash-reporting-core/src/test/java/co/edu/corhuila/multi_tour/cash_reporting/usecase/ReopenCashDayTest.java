package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR_WITHOUT_PERMISSION;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import org.junit.jupiter.api.Test;

class ReopenCashDayTest {

    private static final long BASE = 30_000_000L;
    private static final String REASON = "Se omitio un pago operacional en efectivo";

    private final CashFixture world = new CashFixture();

    /** A cash of today with its operational opening, already closed by the Administrator (closure-1). */
    private CashClosure seedClosedDay() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));
        return world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");
    }

    @Test
    void reopeningNeedsAReasonAndKeepsWhoWhenAndWhy() {
        seedClosedDay();
        int sessionWrites = world.sessions.writes();
        int keyWrites = world.keys.writes();

        assertThatThrownBy(() -> world.reopenCashDay().execute(ADMIN, TODAY, "   ", "key-reopen-0001"))
                .isInstanceOf(CashRuleViolation.ReasonRequired.class);
        assertThatThrownBy(() -> world.reopenCashDay().execute(ADMIN, TODAY, null, "key-reopen-0001"))
                .isInstanceOf(CashRuleViolation.ReasonRequired.class);
        assertThat(world.sessions.writes()).isEqualTo(sessionWrites);
        assertThat(world.keys.writes()).isEqualTo(keyWrites);
        assertThat(world.reopenings.writes()).isZero();
        assertThat(world.outbox.events()).hasSize(1);

        world.reopenCashDay().execute(COLLABORATOR, TODAY, "  " + REASON + "  ", "key-reopen-0001");

        assertThat(world.reopenings.rows()).singleElement().satisfies(reopening -> {
            assertThat(reopening.id()).isEqualTo("reopening-2");
            assertThat(reopening.date()).isEqualTo(TODAY);
            assertThat(reopening.reason()).isEqualTo(REASON);
            assertThat(reopening.reopenedBy()).isEqualTo("collab-1");
            assertThat(reopening.reopenedAt()).isEqualTo(NOW);
            assertThat(reopening.closureId()).isEqualTo("closure-1");
        });
        assertThat(world.outbox.events()).hasSize(2);
        assertThat(world.outbox.events().get(1)).satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("CashSessionReopened");
            assertThat(event.tenantId()).isEqualTo(TENANT);
            assertThat(event.aggregateId()).isEqualTo("2026-10-14");
            assertThat(event.payload())
                    .containsEntry("businessDate", "2026-10-14")
                    .containsEntry("closureId", "closure-1")
                    .containsEntry("reopeningId", "reopening-2")
                    .doesNotContainKey("reason");
        });
        assertThat(world.keys.find(TENANT, "key-reopen-0001")).isPresent();
        assertThat(world.unitOfWork.commits()).isEqualTo(2);
    }

    @Test
    void reopeningCreatesNoSecondCashAndKeepsTheClosure() {
        CashClosure closure = seedClosedDay();

        CashSession reopened = world.reopenCashDay().execute(ADMIN, TODAY, REASON, "key-reopen-0001");

        assertThat(reopened.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(reopened.closingMode()).isNull();
        assertThat(reopened.origin()).isEqualTo(Origin.OPERATIONAL_OPENING);
        assertThat(reopened.opening().base().value()).isEqualTo(BASE);
        assertThat(world.sessions.count()).isEqualTo(1);
        assertThat(world.sessions.find(TENANT, TODAY)).contains(reopened);
        assertThat(world.closures.all(TENANT, TODAY)).containsExactly(closure);
        assertThat(world.closures.writes()).isEqualTo(1);
    }

    @Test
    void aKeyOfAnotherOperationIsRefused() {
        seedClosedDay();
        int events = world.outbox.events().size();
        world.keys.put(TENANT, "key-foreign-001", "CASH_MOVEMENT", "6f1c1c64-0000-4000-8000-000000000001");

        assertThatThrownBy(() -> world.reopenCashDay().execute(ADMIN, TODAY, REASON, "key-foreign-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");

        assertThat(world.reopenings.writes()).isZero();
        assertThat(world.outbox.events()).hasSize(events);
    }

    @Test
    void aRepeatedReopeningKeyReturnsTheDayWithoutWritingAgain() {
        seedClosedDay();
        ReopenCashDay reopen = world.reopenCashDay();
        CashSession first = reopen.execute(ADMIN, TODAY, REASON, "key-reopen-0001");

        CashSession again = reopen.execute(ADMIN, TODAY, REASON, "key-reopen-0001");

        assertThat(again).isEqualTo(first);
        assertThat(world.reopenings.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).hasSize(2);
        assertThat(world.unitOfWork.commits()).isEqualTo(2);
    }

    @Test
    void aCollaboratorWithoutPermissionCannotReopen() {
        seedClosedDay();
        int sessionWrites = world.sessions.writes();

        assertThatThrownBy(() -> world.reopenCashDay()
                        .execute(COLLABORATOR_WITHOUT_PERMISSION, TODAY, REASON, "key-reopen-0001"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);

        assertThat(world.sessions.writes()).isEqualTo(sessionWrites);
        assertThat(world.reopenings.writes()).isZero();
    }

    @Test
    void aDayThatIsNotClosedCannotBeReopened() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));

        assertThatThrownBy(() -> world.reopenCashDay().execute(ADMIN, TODAY, REASON, "key-reopen-0001"))
                .isInstanceOf(CashRuleViolation.AlreadyOpen.class);

        world.assertNothingWasWritten();
    }

    @Test
    void reopeningADateWithoutCashIsNotFound() {
        assertThatThrownBy(() -> world.reopenCashDay().execute(ADMIN, TODAY, REASON, "key-reopen-0001"))
                .isInstanceOf(CashRuleViolation.NotFound.class);

        world.assertNothingWasWritten();
    }

    @Test
    void anAutomaticClosureCannotBeReopened() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE).close(ClosingMode.AUTOMATIC));

        assertThatThrownBy(() -> world.reopenCashDay().execute(ADMIN, TODAY, REASON, "key-reopen-0001"))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aReopenedDayKeepsEachClosing() {
        seedClosedDay();
        ReopenCashDay reopen = world.reopenCashDay();
        reopen.execute(ADMIN, TODAY, REASON, "key-reopen-0001");
        CashClosure second = world.closeCashDay().execute(ADMIN, TODAY, "key-close-0003");
        reopen.execute(ADMIN, TODAY, "Otro pago omitido", "key-reopen-0002");

        assertThat(second.id()).isEqualTo("closure-3");
        assertThat(world.closures.all(TENANT, TODAY))
                .extracting(CashClosure::id)
                .containsExactly("closure-1", "closure-3");
        assertThat(world.sessions.count()).isEqualTo(1);
        assertThat(world.reopenings.rows())
                .extracting(CashReopening::id, CashReopening::closureId)
                .containsExactly(tuple("reopening-2", "closure-1"), tuple("reopening-4", "closure-3"));
        assertThat(world.sessions.find(TENANT, TODAY).orElseThrow().status()).isEqualTo(SessionStatus.OPEN);
    }
}
