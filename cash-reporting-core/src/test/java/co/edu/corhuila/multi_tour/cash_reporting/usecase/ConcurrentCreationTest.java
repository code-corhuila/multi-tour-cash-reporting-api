package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Two writers read "no cash" for the same day (FOR UPDATE on zero rows locks nothing) and the other one commits before
 * this one writes. The stale port gives the loser that empty read and runs the winner right after it.
 */
class ConcurrentCreationTest {

    private final CashFixture world = new CashFixture();

    /** The first locked read answers "empty" while the winner commits its day, as in a race. */
    private final class StaleFirstRead implements CashSessionRepository {
        private final Runnable winner;
        private boolean raced;

        StaleFirstRead(Runnable winner) {
            this.winner = winner;
        }

        @Override
        public Optional<CashSession> find(String tenantId, BusinessDate date) {
            return world.sessions.find(tenantId, date);
        }

        @Override
        public Optional<CashSession> findForUpdate(String tenantId, BusinessDate date) {
            if (!raced) {
                raced = true;
                winner.run();
                return Optional.empty();
            }
            return world.sessions.findForUpdate(tenantId, date);
        }

        @Override
        public Page<CashSession> list(String tenantId, PageRequest request, BusinessDate pendingAsOf) {
            return world.sessions.list(tenantId, request, pendingAsOf);
        }

        @Override
        public Page<CashSession> listOpen(PageRequest request) {
            return world.sessions.listOpen(request);
        }

        @Override
        public CashSession save(String tenantId, CashSession session) {
            return world.sessions.save(tenantId, session);
        }

        // no @Override on purpose: the creation method of the port arrives with the fix
        public CashSession insert(String tenantId, CashSession session) {
            return world.sessions.insert(tenantId, session);
        }
    }

    private OpenCashDay loserOpening(Runnable winner) {
        return new OpenCashDay(
                world.permissions,
                new StaleFirstRead(winner),
                tenantId -> world.parametrizedBase,
                world.keys,
                world.outbox,
                world.calendar,
                world.clock,
                world.unitOfWork);
    }

    private RegisterCashMovement loserMovement(Runnable winner) {
        return new RegisterCashMovement(
                world.permissions,
                new StaleFirstRead(winner),
                world.movements,
                world.keys,
                world.outbox,
                world.calendar,
                world.clock,
                () -> "movement-" + world.lastId.incrementAndGet(),
                world.unitOfWork);
    }

    @Test
    void anOpeningThatLosesTheRaceToAnotherOpeningIsRefusedAndLeavesTheWinnerIntact() {
        OpenCashDay loser = loserOpening(() -> world.sessions.seed(CashFixture.openedOn(TODAY, 70_000_000L)));

        assertThatThrownBy(() -> loser.execute(ADMIN, TODAY, 10_000_000L, "key-loser-0001"))
                .isInstanceOf(CashRuleViolation.AlreadyOpen.class);

        CashSession kept = world.sessions.find(TENANT, TODAY).orElseThrow();
        assertThat(kept.opening()).isEqualTo(new CashSession.Opening(Cents.of(70_000_000L), NOW, "admin-1"));
        assertThat(world.keys.find(TENANT, "key-loser-0001")).isEmpty();
        assertThat(world.outbox.events()).isEmpty();
    }

    @Test
    void anOpeningThatLosesTheRaceToAnAutomaticCreationOpensTheDayThatNowExists() {
        OpenCashDay loser = loserOpening(() -> world.sessions.seed(CashSession.automatic(TENANT, TODAY, BOGOTA)));

        CashSession day = loser.execute(COLLABORATOR, TODAY, null, "key-loser-0002");

        assertThat(world.sessions.count()).isEqualTo(1);
        assertThat(day.origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(day.hasOperationalOpening()).isTrue();
        assertThat(world.keys.find(TENANT, "key-loser-0002")).isPresent();
        assertThat(world.outbox.events()).extracting(event -> event.eventType()).containsExactly("CashSessionOpened");
    }

    @Test
    void aTransferThatLosesTheRaceToAnAutomaticCreationRecordsOnTheExistingDay() {
        RegisterCashMovement loser =
                loserMovement(() -> world.sessions.seed(CashSession.automatic(TENANT, TODAY, BOGOTA)));

        loser.execute(
                ADMIN,
                MovementSource.MANUAL,
                TODAY,
                MovementType.INCOME,
                PaymentMedium.TRANSFER,
                "Ingreso por transferencia",
                5_000_000L,
                "key-loser-0003");

        assertThat(world.sessions.count()).isEqualTo(1);
        assertThat(world.outbox.events())
                .as("the day was created by the winner, not by this writer")
                .extracting(event -> event.eventType())
                .containsExactly("CashIncomeRecorded");
        assertThat(world.keys.find(TENANT, "key-loser-0003")).isPresent();
    }
}
