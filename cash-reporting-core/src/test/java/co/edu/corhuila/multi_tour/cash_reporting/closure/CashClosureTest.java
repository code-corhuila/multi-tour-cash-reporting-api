package co.edu.corhuila.multi_tour.cash_reporting.closure;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashClosureTest {

    private static final BusinessDate DAY = BusinessDate.of(LocalDate.of(2026, 10, 14));
    private static final Instant AT = Instant.parse("2026-10-14T23:00:00Z");

    @Test
    void aClosureKeepsValuesAndEntriesAndExitsByMedium() {
        // The portal demo day: base 30000000, income 129000000 by transfer, expense 25000000 in cash,
        // refund 15000000 by transfer.
        CashSession session = CashSession.openNew(
                "tenant-1",
                DAY,
                ZoneId.of("America/Bogota"),
                new CashSession.Opening(Cents.of(30_000_000L), AT, "admin-1"),
                DAY);
        DayView view = DayTotals.compute(
                Cents.of(30_000_000L),
                List.of(
                        new CashMovement(MovementType.INCOME, PaymentMedium.TRANSFER, Cents.of(129_000_000L)),
                        new CashMovement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, Cents.of(25_000_000L)),
                        new CashMovement(MovementType.REFUND, PaymentMedium.TRANSFER, Cents.of(15_000_000L))));

        CashClosure closure = CashClosure.snapshot("closure-1", session, view, ClosingMode.MANUAL, "admin-1", AT, false);

        assertThat(closure.id()).isEqualTo("closure-1");
        assertThat(closure.tenantId()).isEqualTo("tenant-1");
        assertThat(closure.date()).isEqualTo(DAY);
        assertThat(closure.origin()).isEqualTo(Origin.OPERATIONAL_OPENING);
        assertThat(closure.mode()).isEqualTo(ClosingMode.MANUAL);
        assertThat(closure.closedBy()).isEqualTo("admin-1");
        assertThat(closure.closedAt()).isEqualTo(AT);
        assertThat(closure.formalClosureOfPending()).isFalse();
        assertThat(closure.view().openingBaseCents()).isEqualTo(Cents.of(30_000_000L));
        assertThat(closure.view().incomeCents()).isEqualTo(Cents.of(129_000_000L));
        assertThat(closure.view().operationalPaymentsCents()).isEqualTo(Cents.ZERO);
        assertThat(closure.view().expensesCents()).isEqualTo(Cents.of(25_000_000L));
        assertThat(closure.view().refundsCents()).isEqualTo(Cents.of(15_000_000L));
        assertThat(closure.view().cashEntriesCents()).isEqualTo(Cents.ZERO);
        assertThat(closure.view().cashExitsCents()).isEqualTo(Cents.of(25_000_000L));
        assertThat(closure.view().transferEntriesCents()).isEqualTo(Cents.of(129_000_000L));
        assertThat(closure.view().transferExitsCents()).isEqualTo(Cents.of(15_000_000L));
        assertThat(closure.view().expectedCashCents()).isEqualTo(Cents.of(5_000_000L));
        assertThat(closure.closingBalance()).isEqualTo(Cents.of(119_000_000L));
    }
}
