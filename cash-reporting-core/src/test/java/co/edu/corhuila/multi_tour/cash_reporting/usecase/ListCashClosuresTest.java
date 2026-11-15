package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.CUSTOMER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListCashClosuresTest {

    private static final BusinessDate OCT_11 = BusinessDate.of(LocalDate.of(2026, 10, 11));
    private static final BusinessDate OCT_12 = BusinessDate.of(LocalDate.of(2026, 10, 12));
    private static final BusinessDate OCT_13 = BusinessDate.of(LocalDate.of(2026, 10, 13));

    private final CashFixture world = new CashFixture();

    /** A closure of a day with base 10,000,000 and one cash income of 500: its total is 10,000,500. */
    private static CashClosure closure(String id, BusinessDate date, ClosingMode mode, String closedAt, String tenant) {
        var view = DayTotals.compute(
                Cents.of(10_000_000),
                List.of(new CashMovement(MovementType.INCOME, PaymentMedium.CASH, Cents.of(500))));
        return new CashClosure(
                id, tenant, date, Origin.OPERATIONAL_OPENING, mode, view, "admin-1", Instant.parse(closedAt), false);
    }

    private void reopenOct13FirstClosure() {
        world.reopenings.insert(
                TENANT,
                new CashReopening(
                        "r-1", TENANT, OCT_13, "c-13a", "Falto un pago", "admin-1", Instant.parse("2026-10-13T23:00:00Z")));
    }

    private void seedReopenedOct13() {
        world.closures.insert(TENANT, closure("c-13a", OCT_13, ClosingMode.MANUAL, "2026-10-13T22:00:00Z", TENANT));
        reopenOct13FirstClosure();
        world.closures.insert(TENANT, closure("c-13b", OCT_13, ClosingMode.MANUAL, "2026-10-14T01:00:00Z", TENANT));
    }

    @Test
    void closuresAreListedNewestFirstWithBadgeAndReopenings() {
        world.closures.insert(TENANT, closure("c-11", OCT_11, ClosingMode.AUTOMATIC, "2026-10-11T23:00:00Z", TENANT));
        world.closures.insert(TENANT, closure("c-12", OCT_12, ClosingMode.MANUAL, "2026-10-12T23:00:00Z", TENANT));
        world.closures.insert(TENANT, closure("c-13a", OCT_13, ClosingMode.MANUAL, "2026-10-13T22:00:00Z", TENANT));
        world.closures.insert(
                "tenant-2", closure("c-other", OCT_13, ClosingMode.MANUAL, "2026-10-13T23:30:00Z", "tenant-2"));
        reopenOct13FirstClosure();
        world.corrections.insert(
                TENANT,
                new CashCorrection(
                        "k-1", TENANT, OCT_13, "c-13a", "Ajuste excepcional", "admin-1", Instant.parse("2026-10-14T00:00:00Z")));
        int writes = world.closures.writes() + world.reopenings.writes() + world.corrections.writes();

        Page<ClosureEntry> page = world.listCashClosures().execute(ADMIN, PageRequest.of(null, null));

        assertThat(page.items()).extracting(entry -> entry.closure().id()).containsExactly("c-13a", "c-12", "c-11");
        assertThat(page.items())
                .extracting(ClosureEntry::badge)
                .containsExactly(ClosureBadge.REOPENED, ClosureBadge.CLOSED, ClosureBadge.AUTOMATIC_CLOSING);
        assertThat(page.items().get(0).reopenings()).singleElement().satisfies(reopening -> {
            assertThat(reopening.reason()).isEqualTo("Falto un pago");
            assertThat(reopening.reopenedBy()).isEqualTo("admin-1");
        });
        assertThat(page.items().get(0).corrections())
                .extracting(CashCorrection::justification)
                .containsExactly("Ajuste excepcional");
        assertThat(page.items().get(1).reopenings()).isEmpty();
        assertThat(page.items().get(1).corrections()).isEmpty();
        assertThat(page.items().get(0).closure().view().totalCents()).isEqualTo(Cents.of(10_000_500));
        assertThat(page.total()).isEqualTo(3);
        assertThat(world.closures.writes() + world.reopenings.writes() + world.corrections.writes())
                .isEqualTo(writes);
    }

    @Test
    void aReopenedDayShowsOneEntryPerClosing() {
        seedReopenedOct13();

        Page<ClosureEntry> page = world.listCashClosures().execute(ADMIN, PageRequest.of(null, null));

        assertThat(page.items()).hasSize(2).allMatch(entry -> entry.closure().date().equals(OCT_13));
        assertThat(page.items()).extracting(entry -> entry.closure().id()).containsExactly("c-13b", "c-13a");
        assertThat(page.items())
                .extracting(ClosureEntry::badge)
                .containsExactly(ClosureBadge.CLOSED, ClosureBadge.REOPENED);
        assertThat(page.items().get(0).reopenings()).isEmpty();
        assertThat(page.items().get(1).reopenings()).hasSize(1);
    }

    @Test
    void theHistoryIsPaginatedAndNeedsATenantRole() {
        seedReopenedOct13();

        Page<ClosureEntry> second = world.listCashClosures().execute(ADMIN, PageRequest.of(2, 1));

        assertThat(second.items()).extracting(entry -> entry.closure().id()).containsExactly("c-13a");
        assertThat(second.totalPages()).isEqualTo(2);
        assertThatThrownBy(() -> world.listCashClosures().execute(CUSTOMER, PageRequest.of(null, null)))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
    }
}
