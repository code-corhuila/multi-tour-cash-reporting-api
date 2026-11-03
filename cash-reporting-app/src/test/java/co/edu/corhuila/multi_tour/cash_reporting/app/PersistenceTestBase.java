package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration level (I): the adapters against a migrated PostgreSQL, as the application user. A subclass carries
 * {@link RequiresTestDatabase}, because JUnit does not inherit that condition.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class PersistenceTestBase {

    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    static final Instant OPENED_AT = Instant.parse("2026-11-03T13:00:00Z");
    static final Instant CLOSED_AT = Instant.parse("2026-11-04T02:00:00Z");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    CashSessionRepository sessions;

    @Autowired
    CashClosureRepository closures;

    @Autowired
    CashReopeningRepository reopenings;

    @Autowired
    CashMovementRepository movements;

    @Autowired
    IdempotencyStore keys;

    @Autowired
    OutboxWriter outbox;

    @Autowired
    UnitOfWork unitOfWork;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static BusinessDate day(int dayOfMonth) {
        return BusinessDate.of(LocalDate.of(2026, 11, dayOfMonth));
    }

    /** A cash opened by the operator on its own date, with a base of 50,000 cents. */
    static CashSession opened(String tenant, BusinessDate date, String user) {
        return CashSession.openNew(
                tenant, date, ZONE, new CashSession.Opening(Cents.of(50_000), OPENED_AT, user), date);
    }

    /** The raw row of a day, read by SQL: what the columns really hold. */
    Map<String, Object> rowOf(String tenant, BusinessDate date) {
        return jdbc.queryForMap(
                "select * from cash_reporting.cash_session where tenant_id = ?::uuid and business_date = ?",
                tenant,
                Date.valueOf(date.value()));
    }

    /** Writes the closure of a day by SQL, with figures that satisfy the arithmetic checks of the table. */
    void insertClosureRow(String tenant, BusinessDate date, String closedBy) {
        jdbc.update(
                "insert into cash_reporting.cash_session_closure (cash_session_closure_id, tenant_id,"
                        + " cash_session_id, closed_at, closing_mode, closed_by, opening_base_cents, income_cents,"
                        + " operational_payments_cents, operational_expenses_cents, refunds_cents, cash_entries_cents,"
                        + " transfer_entries_cents, cash_exits_cents, transfer_exits_cents, expected_cash_cents,"
                        + " closing_balance_cents) select ?::uuid, tenant_id, cash_session_id, ?, 'FORMAL', ?::uuid,"
                        + " 50000, 30000, 5000, 2000, 1000, 20000, 10000, 7000, 1000, 63000, 72000"
                        + " from cash_reporting.cash_session where tenant_id = ?::uuid and business_date = ?",
                newId(),
                Timestamp.from(CLOSED_AT),
                closedBy,
                tenant,
                Date.valueOf(date.value()));
    }

    /** Figures that satisfy the arithmetic checks of the closure table. */
    static DayView figures() {
        return new DayView(
                Cents.of(50_000),
                Cents.of(30_000),
                Cents.of(5_000),
                Cents.of(2_000),
                Cents.of(1_000),
                Cents.of(72_000),
                Cents.of(20_000),
                Cents.of(7_000),
                Cents.of(10_000),
                Cents.of(1_000),
                Cents.of(63_000),
                Cents.of(9_000));
    }

    /** A stored movement of a day; a REFUND carries the reservation it gives money back for. */
    static RecordedMovement recorded(
            BusinessDate date, MovementType type, PaymentMedium medium, long cents, String by, Instant at) {
        RefundDetail refund = type == MovementType.REFUND ? new RefundDetail(newId()) : null;
        return new RecordedMovement(
                newId(), date, new CashMovement(type, medium, Cents.amount(cents)), "Concept", false, at, by, refund);
    }

    /** The closure of a day as the use case builds it: late when it is signed on a date after its own. */
    static CashClosure closureOf(CashSession session, ClosingMode mode, String closedBy, Instant closedAt) {
        boolean late = BusinessDate.of(closedAt, session.zone()).isAfter(session.date());
        return CashClosure.snapshot(newId(), session, figures(), mode, closedBy, closedAt, late);
    }

    /** Inserts the closure, then closes the session: the order the close use case follows. */
    CashClosure close(String tenant, CashSession session, String closedBy) {
        CashClosure closure = closureOf(session, ClosingMode.MANUAL, closedBy, CLOSED_AT);
        closures.insert(tenant, closure);
        sessions.save(tenant, session.close(ClosingMode.MANUAL));
        return closure;
    }
}
