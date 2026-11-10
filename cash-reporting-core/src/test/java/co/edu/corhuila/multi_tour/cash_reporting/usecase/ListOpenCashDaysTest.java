package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.WORKER;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import org.junit.jupiter.api.Test;

/** The sweep of job 8: every open cash of every tenant, oldest first, with what the worker needs to decide. */
class ListOpenCashDaysTest {

    private static final String OTHER_TENANT = "tenant-2";
    private final CashFixture world = new CashFixture();

    @Test
    void listsTheOpenDaysOfEveryTenantOldestFirstAndSkipsTheClosedOnes() {
        world.sessions.seed(CashSession.automatic(OTHER_TENANT, YESTERDAY, BOGOTA));
        world.sessions.seed(CashFixture.openedOn(TODAY, 1_000_000L));
        world.sessions.seed(CashSession.automatic(TENANT, YESTERDAY, BOGOTA));
        world.sessions.seed(CashFixture.openedOn(CashFixture.TOMORROW, 1_000_000L).close(ClosingMode.MANUAL));

        Page<OpenCashDayItem> page = world.listOpenCashDays().execute(WORKER, PageRequest.of(1, 20));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(OpenCashDayItem::tenantId).containsExactly(TENANT, OTHER_TENANT, TENANT);
        assertThat(page.items()).extracting(item -> item.date().value())
                .containsExactly(YESTERDAY.value(), YESTERDAY.value(), TODAY.value());
    }

    @Test
    void carriesTheZoneTheOriginTheOpeningAndThePendingFlag() {
        world.sessions.seed(CashSession.automatic(TENANT, YESTERDAY, BOGOTA));
        world.sessions.seed(CashFixture.openedOn(TODAY, 1_000_000L));

        var items = world.listOpenCashDays().execute(WORKER, PageRequest.of(1, 20)).items();

        OpenCashDayItem automatic = items.get(0);
        assertThat(automatic.zone()).isEqualTo(BOGOTA);
        assertThat(automatic.origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(automatic.hasOperationalOpening()).isFalse();
        assertThat(automatic.pendingClosure()).as("pending needs an operational opening").isFalse();
        OpenCashDayItem opened = items.get(1);
        assertThat(opened.origin()).isEqualTo(Origin.OPERATIONAL_OPENING);
        assertThat(opened.hasOperationalOpening()).isTrue();
        assertThat(opened.pendingClosure()).as("today is not over").isFalse();
    }

    @Test
    void aPastDayWithOpeningIsPending() {
        world.sessions.seed(CashFixture.openedOn(YESTERDAY, 1_000_000L));

        var items = world.listOpenCashDays().execute(WORKER, PageRequest.of(1, 20)).items();

        assertThat(items.get(0).pendingClosure()).isTrue();
    }

    @Test
    void pagesTheList() {
        world.sessions.seed(CashSession.automatic(OTHER_TENANT, YESTERDAY, BOGOTA));
        world.sessions.seed(CashSession.automatic(TENANT, YESTERDAY, BOGOTA));

        Page<OpenCashDayItem> second = world.listOpenCashDays().execute(WORKER, PageRequest.of(2, 1));

        assertThat(second.total()).isEqualTo(2);
        assertThat(second.items()).hasSize(1);
    }

    @Test
    void onlyAServiceMayList() {
        assertThatThrownBy(() -> world.listOpenCashDays().execute(ADMIN, PageRequest.of(1, 20)))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
    }
}
