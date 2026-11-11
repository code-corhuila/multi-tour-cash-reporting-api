package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.ZonedTenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryIdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryOutbox;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryUnitOfWork;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** The shared world of the use case tests: a clock, a tenant calendar, the fakes and a few actors. */
final class CashFixture {

    static final String TENANT = "tenant-1";
    static final String MANAGE_CASH = "Gestionar caja";
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** 15:00 UTC is 10:00 in Bogota: today there is 2026-10-14. */
    static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");

    static final BusinessDate TODAY = BusinessDate.of(LocalDate.of(2026, 10, 14));
    static final BusinessDate YESTERDAY = BusinessDate.of(LocalDate.of(2026, 10, 13));
    static final BusinessDate TOMORROW = BusinessDate.of(LocalDate.of(2026, 10, 15));

    static final Actor ADMIN = new Actor(TENANT, "admin-1", Set.of("TENANT_ADMINISTRATOR"), Set.of());
    static final Actor COLLABORATOR =
            new Actor(TENANT, "collab-1", Set.of("OPERATIONAL_COLLABORATOR"), Set.of(MANAGE_CASH));
    /** The token of a service: no tenant of its own, the role SERVICE, the service name as its subject. */
    static final Actor WORKER = new Actor(null, "multi-tour-worker-cash-close", Set.of("SERVICE"), Set.of());
    static final Actor MANAGER = new Actor(TENANT, "manager-1", Set.of("MANAGER"), Set.of());
    static final Actor CUSTOMER = new Actor(TENANT, "customer-1", Set.of("FINAL_CUSTOMER"), Set.of());
    static final Actor COLLABORATOR_WITHOUT_PERMISSION =
            new Actor(TENANT, "collab-2", Set.of("OPERATIONAL_COLLABORATOR"), Set.of());

    final CashPermissions permissions = new CashPermissions(MANAGE_CASH);
    final Clock clock = () -> NOW;
    final TenantCalendar calendar = new ZonedTenantCalendar(clock, tenantId -> BOGOTA);
    final InMemoryCashSessionRepository sessions = new InMemoryCashSessionRepository();
    final InMemoryIdempotencyStore keys = new InMemoryIdempotencyStore();
    final InMemoryOutbox outbox = new InMemoryOutbox();
    final InMemoryCashMovementRepository movements = new InMemoryCashMovementRepository();
    final InMemoryCashClosureRepository closures = new InMemoryCashClosureRepository();
    final InMemoryCashReopeningRepository reopenings = new InMemoryCashReopeningRepository();
    final InMemoryCashCorrectionRepository corrections = new InMemoryCashCorrectionRepository();
    final InMemoryUnitOfWork unitOfWork = new InMemoryUnitOfWork();
    final co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryPendingClosureAlert alerts =
            new co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryPendingClosureAlert();
    final AtomicInteger lastId = new AtomicInteger();
    Cents parametrizedBase = Cents.ZERO;

    OpenCashDay openCashDay() {
        return new OpenCashDay(permissions, sessions, tenantId -> parametrizedBase, keys, outbox, calendar, clock);
    }

    AdjustCashBase adjustCashBase() {
        return new AdjustCashBase(permissions, sessions, calendar);
    }

    RegisterCashMovement registerCashMovement() {
        return new RegisterCashMovement(
                permissions,
                sessions,
                movements,
                keys,
                outbox,
                calendar,
                clock,
                () -> "movement-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    CloseCashDay closeCashDay() {
        return new CloseCashDay(
                permissions,
                sessions,
                movements,
                closures,
                keys,
                outbox,
                calendar,
                clock,
                () -> "closure-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    ReopenCashDay reopenCashDay() {
        return new ReopenCashDay(
                permissions,
                sessions,
                closures,
                reopenings,
                keys,
                outbox,
                clock,
                () -> "reopening-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    RegisterCashCorrection registerCashCorrection() {
        return new RegisterCashCorrection(
                permissions,
                sessions,
                closures,
                corrections,
                keys,
                clock,
                () -> "correction-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    RecordSagaIncome recordSagaIncome() {
        return new RecordSagaIncome(
                sessions,
                movements,
                keys,
                outbox,
                calendar,
                clock,
                () -> "movement-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    ReverseCashMovement reverseCashMovement() {
        return new ReverseCashMovement(
                sessions,
                movements,
                keys,
                outbox,
                calendar,
                clock,
                () -> "movement-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    AutoCloseCashDay autoCloseCashDay() {
        return new AutoCloseCashDay(
                sessions,
                movements,
                closures,
                outbox,
                alerts,
                clock,
                () -> "closure-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    RelayOutbox relayOutbox() {
        return new RelayOutbox(outbox, clock);
    }

    ListOpenCashDays listOpenCashDays() {
        return new ListOpenCashDays(sessions, calendar);
    }

    RecordExecutedRefund recordExecutedRefund() {
        return new RecordExecutedRefund(
                sessions,
                movements,
                keys,
                outbox,
                calendar,
                clock,
                () -> "movement-" + lastId.incrementAndGet(),
                unitOfWork);
    }

    GetCashDay getCashDay() {
        return new GetCashDay(permissions, sessions, movements, closures, calendar, tenantId -> parametrizedBase);
    }

    ListCashDays listCashDays() {
        return new ListCashDays(permissions, sessions, movements, closures, calendar);
    }

    ListCashMovements listCashMovements() {
        return new ListCashMovements(permissions, movements, calendar);
    }

    ListCashClosures listCashClosures() {
        return new ListCashClosures(permissions, closures, reopenings, corrections);
    }

    /** A cash with operational opening on the given date, opened by admin-1 at NOW. */
    static CashSession openedOn(BusinessDate date, long baseCents) {
        return CashSession.openNew(
                TENANT, date, BOGOTA, new CashSession.Opening(Cents.of(baseCents), NOW, "admin-1"), date);
    }

    void assertNothingWasWritten() {
        assertThat(sessions.writes()).as("session writes").isZero();
        assertThat(keys.writes()).as("key writes").isZero();
        assertThat(outbox.events()).as("events").isEmpty();
        assertThat(movements.writes()).as("movement writes").isZero();
        assertThat(closures.writes()).as("closure writes").isZero();
        assertThat(reopenings.writes()).as("reopening writes").isZero();
        assertThat(corrections.writes()).as("correction writes").isZero();
        assertThat(unitOfWork.commits()).as("commits").isZero();
    }
}
