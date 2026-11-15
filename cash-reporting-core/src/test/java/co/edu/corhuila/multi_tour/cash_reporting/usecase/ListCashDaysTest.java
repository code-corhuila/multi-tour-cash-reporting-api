package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.CUSTOMER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.MANAGER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ListCashDaysTest {

    private static final BusinessDate TWO_DAYS_AGO = BusinessDate.of(LocalDate.of(2026, 10, 12));
    private static final BusinessDate THREE_DAYS_AGO = BusinessDate.of(LocalDate.of(2026, 10, 11));
    private static final BusinessDate FOUR_DAYS_AGO = BusinessDate.of(LocalDate.of(2026, 10, 10));
    private static final BusinessDate OTHER_TENANT_DAY = BusinessDate.of(LocalDate.of(2026, 10, 9));

    private final CashFixture world = new CashFixture();

    /** Today open, yesterday and two days ago open and over, three days ago closed, four days ago automatic. */
    private void seedFiveDays() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000));
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, 20_000_000));
        world.sessions.seed(CashFixture.openedOn(TWO_DAYS_AGO, 10_000_000));
        world.sessions.seed(CashFixture.openedOn(THREE_DAYS_AGO, 5_000_000).close(ClosingMode.MANUAL));
        world.sessions.seed(CashSession.automatic(TENANT, FOUR_DAYS_AGO, BOGOTA));
        world.sessions.seed(CashSession.openNew(
                "tenant-2",
                OTHER_TENANT_DAY,
                BOGOTA,
                new CashSession.Opening(Cents.of(1), NOW, "other-admin"),
                OTHER_TENANT_DAY));
    }

    @Test
    void listingPendingDaysReturnsOnlyPastOpenDays() {
        seedFiveDays();
        world.movements.seed(
                TENANT, YESTERDAY, new CashMovement(MovementType.INCOME, PaymentMedium.CASH, Cents.of(1_000_000)));

        Page<CashDay> pending = world.listCashDays().execute(ADMIN, true, PageRequest.of(null, null));

        assertThat(pending.items()).extracting(CashDay::date).containsExactly(YESTERDAY, TWO_DAYS_AGO);
        assertThat(pending.items()).allMatch(CashDay::pendingClosure);
        assertThat(pending.total()).isEqualTo(2);
        assertThat(pending.items().get(0).figures().incomeCents()).isEqualTo(Cents.of(1_000_000));
        assertThat(pending.items().get(0).figures().totalCents()).isEqualTo(Cents.of(21_000_000));
        world.assertNothingWasWritten();
    }

    @Test
    void withoutTheFilterEveryDayOfTheTenantComesMostRecentFirst() {
        seedFiveDays();

        Page<CashDay> all = world.listCashDays().execute(MANAGER, false, PageRequest.of(null, null));

        assertThat(all.items())
                .extracting(CashDay::date)
                .containsExactly(TODAY, YESTERDAY, TWO_DAYS_AGO, THREE_DAYS_AGO, FOUR_DAYS_AGO);
        assertThat(all.items())
                .extracting(CashDay::status)
                .containsExactly(DayStatus.OPEN, DayStatus.OPEN, DayStatus.OPEN, DayStatus.CLOSED, DayStatus.OPEN);
        assertThat(all.items()).extracting(CashDay::pendingClosure).containsExactly(false, true, true, false, false);
    }

    @Test
    void theListIsPaginatedAndTheLimitIsBounded() {
        seedFiveDays();

        Page<CashDay> second = world.listCashDays().execute(ADMIN, false, PageRequest.of(2, 2));

        assertThat(second.items()).extracting(CashDay::date).containsExactly(TWO_DAYS_AGO, THREE_DAYS_AGO);
        assertThat(second.page()).isEqualTo(2);
        assertThat(second.limit()).isEqualTo(2);
        assertThat(second.total()).isEqualTo(5);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(PageRequest.of(null, null)).isEqualTo(new PageRequest(1, 20));
        assertThat(PageRequest.of(1, 100).limit()).isEqualTo(100);
        assertThatThrownBy(() -> PageRequest.of(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageRequest.of(1, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageRequest.of(0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCustomerCannotListTheDays() {
        assertThatThrownBy(() -> world.listCashDays().execute(CUSTOMER, false, PageRequest.of(null, null)))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
    }
}
