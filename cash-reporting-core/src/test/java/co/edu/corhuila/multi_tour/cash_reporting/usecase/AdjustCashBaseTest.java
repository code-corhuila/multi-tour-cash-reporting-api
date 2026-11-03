package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import org.junit.jupiter.api.Test;

class AdjustCashBaseTest {

    private final CashFixture world = new CashFixture();

    @Test
    void adjustingTheBaseOfAClosedDayIsRefused() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L).close(ClosingMode.MANUAL));

        assertThatThrownBy(() -> world.adjustCashBase().execute(ADMIN, TODAY, 35_000_000L))
                .isInstanceOf(CashRuleViolation.AlreadyClosed.class);

        world.assertNothingWasWritten();
    }

    @Test
    void adjustingWithoutOpeningIsRefusedNoOpenCash() {
        assertThatThrownBy(() -> world.adjustCashBase().execute(ADMIN, TODAY, 35_000_000L))
                .isInstanceOf(CashRuleViolation.NoOpenCash.class);

        world.sessions.seed(CashSession.automatic(TENANT, TODAY, BOGOTA));
        assertThatThrownBy(() -> world.adjustCashBase().execute(ADMIN, TODAY, 35_000_000L))
                .isInstanceOf(CashRuleViolation.NoOpenCash.class);

        world.assertNothingWasWritten();
    }

    @Test
    void adjustingTheBaseOfAPendingDayIsRefused() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, 30_000_000L));

        assertThatThrownBy(() -> world.adjustCashBase().execute(ADMIN, YESTERDAY, 35_000_000L))
                .isInstanceOf(CashRuleViolation.PendingClosure.class);

        world.assertNothingWasWritten();
    }

    @Test
    void theSameBaseAgainChangesNothing() {
        CashSession seeded = CashFixture.openedOn(TODAY, 30_000_000L);
        world.sessions.seed(seeded);

        CashSession day = world.adjustCashBase().execute(ADMIN, TODAY, 30_000_000L);

        assertThat(day).isEqualTo(seeded);
        world.assertNothingWasWritten();
    }

    @Test
    void anInvalidBaseIsRefused() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L));

        assertThatThrownBy(() -> world.adjustCashBase().execute(ADMIN, TODAY, -5L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> world.adjustCashBase().execute(ADMIN, TODAY, null))
                .isInstanceOf(IllegalArgumentException.class);

        world.assertNothingWasWritten();
    }

    @Test
    void theAdministratorAdjustsTheBaseKeepingWhoAndWhen() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L));

        CashSession day = world.adjustCashBase().execute(ADMIN, TODAY, 35_000_000L);

        assertThat(day.opening()).isEqualTo(new CashSession.Opening(Cents.of(35_000_000L), NOW, "admin-1"));
        assertThat(world.sessions.find(TENANT, TODAY)).contains(day);
        assertThat(world.sessions.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).isEmpty();
    }

    @Test
    void aCollaboratorCannotAdjustTheBase() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L));

        assertThatThrownBy(() -> world.adjustCashBase().execute(COLLABORATOR, TODAY, 35_000_000L))
                .isInstanceOf(CashRuleViolation.Forbidden.class);

        world.assertNothingWasWritten();
    }
}
