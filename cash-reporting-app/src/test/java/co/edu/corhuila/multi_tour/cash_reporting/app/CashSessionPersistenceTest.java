package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

@RequiresTestDatabase
class CashSessionPersistenceTest extends PersistenceTestBase {

    private final String tenant = newId();
    private final String user = newId();

    @Test
    void theSessionRoundTripKeepsEveryColumn() {
        var date = day(3);
        CashSession open = opened(tenant, date, user).withStoredPendingMark(true);

        sessions.save(tenant, open);

        assertThat(sessions.find(tenant, date)).contains(open);
        assertThat(sessions.find(newId(), date)).isEmpty();
        Map<String, Object> row = rowOf(tenant, date);
        assertThat(row)
                .containsEntry("time_zone", "America/Bogota")
                .containsEntry("origin", "OPERATIONAL_OPENING")
                .containsEntry("status", "OPEN")
                .containsEntry("opening_base_cents", 50_000L)
                .containsEntry("opened_by", UUID.fromString(user))
                .containsEntry("pending_closure", true);
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void aClosedSessionTakesItsClosingColumnsFromItsClosure() {
        var date = day(3);
        CashSession open = opened(tenant, date, user);
        sessions.save(tenant, open);
        insertClosureRow(tenant, date, user);

        CashSession closed = sessions.save(tenant, open.close(ClosingMode.MANUAL));

        assertThat(sessions.find(tenant, date)).contains(closed);
        assertThat(rowOf(tenant, date))
                .containsEntry("status", "CLOSED")
                .containsEntry("closing_mode", "FORMAL")
                .containsEntry("closing_balance_cents", 72_000L)
                .containsEntry("closed_by", UUID.fromString(user))
                .containsEntry("pending_closure", false);
    }

    @Test
    void aClosureRoundTripKeepsEveryFigure() {
        var date = day(3);
        CashSession open = opened(tenant, date, user);
        sessions.save(tenant, open);
        CashClosure closure = closureOf(open, ClosingMode.MANUAL, user, CLOSED_AT);

        closures.insert(tenant, closure);

        assertThat(closures.findLatest(tenant, date)).contains(closure);
        assertThat(closures.findLatest(newId(), date)).isEmpty();
        Map<String, Object> row = jdbc.queryForMap(
                "select * from cash_reporting.cash_session_closure where tenant_id = ?::uuid", tenant);
        assertThat(row)
                .containsEntry("closing_mode", "FORMAL")
                .containsEntry("closed_by", UUID.fromString(user))
                .containsEntry("opening_base_cents", 50_000L)
                .containsEntry("expected_cash_cents", 63_000L)
                .containsEntry("closing_balance_cents", 72_000L);
    }

    @Test
    void aClosureSignedAfterItsDayIsTheFormalClosureOfPending() {
        var date = day(3);
        CashSession open = opened(tenant, date, user);
        sessions.save(tenant, open);
        CashClosure late = closureOf(open, ClosingMode.MANUAL, user, Instant.parse("2026-11-04T15:00:00Z"));

        closures.insert(tenant, late);

        assertThat(late.formalClosureOfPending()).isTrue();
        assertThat(closures.findLatest(tenant, date)).contains(late);
    }

    @Test
    void anAutomaticClosureKeepsNoUserInTheTable() {
        var date = day(3);
        CashSession open = opened(tenant, date, user);
        sessions.save(tenant, open);
        CashClosure automatic = closureOf(open, ClosingMode.AUTOMATIC, "SYSTEM", CLOSED_AT);

        closures.insert(tenant, automatic);
        sessions.save(tenant, open.close(ClosingMode.AUTOMATIC));

        assertThat(closures.findLatest(tenant, date)).contains(automatic);
        assertThat(rowOf(tenant, date))
                .containsEntry("closing_mode", "AUTOMATIC")
                .containsEntry("closed_by", null);
    }

    @Test
    void twoConcurrentClosesLeaveOneClosure() throws Exception {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        var barrier = new CyclicBarrier(2);
        Callable<Object> closing = () -> transaction().execute(status -> {
            await(barrier);
            CashSession session = sessions.findForUpdate(tenant, date).orElseThrow();
            if (session.status() == SessionStatus.CLOSED) {
                throw new CashRuleViolation.AlreadyClosed("closed");
            }
            return close(tenant, session, user);
        });

        List<Throwable> failures = runBoth(closing);

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(CashRuleViolation.AlreadyClosed.class);
        assertThat(count("cash_session_closure")).isEqualTo(1);
    }

    @Test
    void reopeningClearsTheClosingColumnsAndKeepsTheClosureRow() {
        var date = day(3);
        CashSession open = opened(tenant, date, user);
        sessions.save(tenant, open);
        CashClosure closure = close(tenant, open, user);
        var reopening = new CashReopening(newId(), tenant, date, closure.id(), "Wrong count", user, CLOSED_AT);

        reopenings.insert(tenant, reopening);
        CashSession reopened = sessions.save(tenant, sessions.find(tenant, date).orElseThrow().reopen());

        assertThat(reopened.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(rowOf(tenant, date))
                .containsEntry("status", "OPEN")
                .containsEntry("closed_at", null)
                .containsEntry("closing_mode", null)
                .containsEntry("closing_balance_cents", null)
                .containsEntry("closed_by", null);
        assertThat(count("cash_session")).isEqualTo(1);
        assertThat(count("cash_session_closure")).isEqualTo(1);
        assertThat(closures.findLatest(tenant, date)).contains(closure);
        assertThat(reopenings.listByClosures(tenant, List.of(closure.id()))).containsExactly(reopening);
        assertThat(reopenings.listByClosures(newId(), List.of(closure.id()))).isEmpty();
    }

    @Test
    void twoConcurrentOpeningsLeaveOneCash() throws Exception {
        var date = day(3);
        var barrier = new CyclicBarrier(2);
        Callable<Object> opening = () -> transaction().execute(status -> {
            sessions.findForUpdate(tenant, date);
            await(barrier);
            return sessions.save(tenant, opened(tenant, date, user));
        });

        List<Throwable> failures = runBoth(opening);

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(CashRuleViolation.AlreadyOpen.class);
        assertThat(count("cash_session")).isEqualTo(1);
    }

    @Test
    void theRowLockMakesTheSecondWriterWaitAndSeeTheFirstWrite() throws Exception {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        insertClosureRow(tenant, date, user);
        var barrier = new CyclicBarrier(2);
        Callable<Object> closing = () -> transaction().execute(status -> {
            await(barrier);
            CashSession session = sessions.findForUpdate(tenant, date).orElseThrow();
            if (session.status() == SessionStatus.CLOSED) {
                throw new CashRuleViolation.AlreadyClosed("closed");
            }
            return sessions.save(tenant, session.close(ClosingMode.MANUAL));
        });

        List<Throwable> failures = runBoth(closing);

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(CashRuleViolation.AlreadyClosed.class);
    }

    @Test
    void aMovementWaitsForTheClosingThatHoldsTheDay() throws Exception {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        insertClosureRow(tenant, date, user);
        var locked = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> closing = pool.submit(() -> transaction().execute(status -> {
                CashSession session = sessions.findForUpdate(tenant, date).orElseThrow();
                locked.countDown();
                pause(1_000);
                return sessions.save(tenant, session.close(ClosingMode.MANUAL));
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            long start = System.nanoTime();

            transaction().execute(status -> {
                CashSession seen = sessions.findForUpdate(tenant, date).orElseThrow();
                assertThat(seen.status()).isEqualTo(SessionStatus.CLOSED);
                return movements.insert(
                        tenant,
                        recorded(date, MovementType.INCOME, PaymentMedium.TRANSFER, 9_000, user, CLOSED_AT));
            });

            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            closing.get(10, TimeUnit.SECONDS);
            assertThat(waitedMillis).isGreaterThanOrEqualTo(600);
            assertThat(count("cash_movement")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theDayListUsesOneGroupedQuery() {
        long few = statementsToListDays(2);
        long many = statementsToListDays(6);

        assertThat(many).isEqualTo(few);
        assertThat(many).isLessThanOrEqualTo(3);
    }

    @Test
    void theClosureHistoryReadsItsReopeningsInOneQuery() {
        var ids = new ArrayList<String>();
        for (int d = 1; d <= 4; d++) {
            CashSession open = opened(tenant, day(d), user);
            sessions.save(tenant, open);
            CashClosure closure = close(tenant, open, user);
            reopenings.insert(
                    tenant, new CashReopening(newId(), tenant, day(d), closure.id(), "Why", user, CLOSED_AT));
            ids.add(closure.id());
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(reopenings.listByClosures(tenant, ids)).hasSize(4);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        var page = closures.list(tenant, new PageRequest(1, 3));
        assertThat(page.total()).isEqualTo(4);
        assertThat(page.items()).hasSize(3);
        assertThat(page.items()).extracting(CashClosure::closedAt).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void listingPendingDaysReturnsOnlyOpenDaysAlreadyOver() {
        sessions.save(tenant, opened(tenant, day(2), user));
        sessions.save(tenant, opened(tenant, day(3), user));
        sessions.save(tenant, opened(tenant, day(4), user).withStoredPendingMark(true));
        sessions.save(tenant, CashSession.automatic(tenant, day(1), ZONE));

        var pending = sessions.list(tenant, new PageRequest(1, 10), day(3));

        assertThat(pending.items()).extracting(CashSession::date).containsExactly(day(4), day(2));
        assertThat(pending.total()).isEqualTo(2);
        assertThat(sessions.list(tenant, new PageRequest(1, 10), null).total()).isEqualTo(4);
    }

    @Test
    void ddlAutoIsNone() {
        Object ddl = entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto");

        assertThat(ddl == null || "none".equals(ddl)).isTrue();
        assertThatThrownBy(() -> jdbc.queryForObject("select count(*) from cash_reporting.not_a_table", Integer.class))
                .isInstanceOf(DataAccessException.class);
    }

    private long statementsToListDays(int days) {
        String owner = newId();
        for (int d = 1; d <= days; d++) {
            CashSession open = opened(owner, day(d), user);
            sessions.save(owner, open);
            close(owner, open, user);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = sessions.list(owner, new PageRequest(1, 20), null);
        var latest = closures.findLatestByDates(
                owner, page.items().stream().map(CashSession::date).toList());

        assertThat(page.items()).hasSize(days);
        assertThat(page.items().get(0).date()).isEqualTo(day(days));
        assertThat(latest).hasSize(days);
        return statistics.getPrepareStatementCount();
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid", Integer.class, tenant);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs the same work on two threads, each with its own connection, and returns the failures. */
    private static List<Throwable> runBoth(Callable<Object> work) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = pool.invokeAll(List.of(work, work));
            var failures = new ArrayList<Throwable>();
            for (Future<Object> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }
}
