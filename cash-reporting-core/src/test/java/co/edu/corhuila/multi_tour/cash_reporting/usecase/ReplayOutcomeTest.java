package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import org.junit.jupiter.api.Test;

/** The use case says whether the answer is a replay of the key; the controller only maps it to 200 or 201. */
class ReplayOutcomeTest {

    private static final long BASE = 30_000_000L;
    private final CashFixture world = new CashFixture();

    private Outcome<RecordedMovement> transfer(String key) {
        return world.registerCashMovement().register(ADMIN, MovementSource.MANUAL, TODAY, MovementType.INCOME,
                PaymentMedium.TRANSFER, "Abono", 5_000L, key);
    }

    private Outcome<CashCorrection> correction(String key) {
        return world.registerCashCorrection().register(ADMIN, TODAY, "Se corrige la descripcion", key);
    }

    private void closedDay() {
        world.sessions.seed(CashFixture.openedOn(TODAY, BASE));
        world.closeCashDay().execute(ADMIN, TODAY, "key-close-0001");
    }

    @Test
    void theFirstMovementIsNotAReplayAndTheSecondOneIs() {
        Outcome<RecordedMovement> first = transfer("key-move-0001");
        Outcome<RecordedMovement> again = transfer("key-move-0001");

        assertThat(first.replayed()).isFalse();
        assertThat(again.replayed()).isTrue();
        assertThat(again.value()).isEqualTo(first.value());
        assertThat(world.movements.writes()).isEqualTo(1);
    }

    @Test
    void aMovementWriterThatReadNoKeyButLostTheCommitAnswersTheWinnersMovementAsAReplay() {
        Outcome<RecordedMovement> winner = transfer("key-race-0001");
        world.keys.blindNextFind();

        Outcome<RecordedMovement> loser = transfer("key-race-0001");

        assertThat(loser.replayed()).isTrue();
        assertThat(loser.value()).isEqualTo(winner.value());
        assertThat(world.keys.writes()).isEqualTo(1);
        assertThat(world.unitOfWork.rollbacks()).as("the loser's transaction is rolled back").isEqualTo(1);
    }

    @Test
    void theFirstCorrectionIsNotAReplayAndTheSecondOneIs() {
        closedDay();

        Outcome<CashCorrection> first = correction("key-corr-0001");
        Outcome<CashCorrection> again = correction("key-corr-0001");

        assertThat(first.replayed()).isFalse();
        assertThat(again.replayed()).isTrue();
        assertThat(again.value()).isEqualTo(first.value());
    }

    @Test
    void aCorrectionWriterThatReadNoKeyButLostTheCommitAnswersTheWinnersCorrectionAsAReplay() {
        closedDay();
        Outcome<CashCorrection> winner = correction("key-corr-race1");
        world.keys.blindNextFind();

        Outcome<CashCorrection> loser = correction("key-corr-race1");

        assertThat(loser.replayed()).isTrue();
        assertThat(loser.value()).isEqualTo(winner.value());
        assertThat(world.unitOfWork.rollbacks()).isEqualTo(1);
        assertThat(world.keys.find(TENANT, "key-corr-race1")).isPresent();
    }
}
