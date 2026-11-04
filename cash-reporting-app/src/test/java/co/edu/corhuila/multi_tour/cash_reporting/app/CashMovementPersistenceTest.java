package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Integration level (I): the movements, the keys and the outbox against a migrated PostgreSQL. */
@RequiresTestDatabase
class CashMovementPersistenceTest extends PersistenceTestBase {

    private static final List<String> EVENTS = List.of(
            "CashSessionOpened",
            "CashSessionClosed",
            "CashSessionAutoCreated",
            "CashSessionAutoClosed",
            "CashSessionReopened",
            "CashIncomeRecorded",
            "OperationalPaymentRecorded",
            "OperationalExpenseRecorded",
            "RefundExecuted");

    private final String tenant = newId();
    private final String user = newId();

    @Test
    void theMovementItsKeyAndItsEventAreOneTransaction() {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        RecordedMovement kept = recorded(date, MovementType.INCOME, PaymentMedium.CASH, 12_000, user, OPENED_AT);

        unitOfWork.inTransaction(() -> {
            movements.insert(tenant, kept);
            keys.put(tenant, "key-committed", "CASH_MOVEMENT", kept.id());
            outbox.append(tenant, "CashIncomeRecorded", kept.id(), Map.of("movementId", kept.id()));
            return kept;
        });

        assertThat(count("cash_movement")).isEqualTo(1);
        assertThat(count("idempotency_key")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);

        RecordedMovement lost = recorded(date, MovementType.INCOME, PaymentMedium.CASH, 7_000, user, OPENED_AT);
        assertThatThrownBy(() -> unitOfWork.inTransaction(() -> {
                    movements.insert(tenant, lost);
                    keys.put(tenant, "key-rolled-back", "CASH_MOVEMENT", lost.id());
                    outbox.append(tenant, "NotAnEventOfTheCatalogue", lost.id(), Map.of());
                    return lost;
                }))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("cash_movement")).isEqualTo(1);
        assertThat(count("idempotency_key")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
        assertThat(keys.find(tenant, "key-rolled-back")).isEmpty();
        assertThat(movements.findById(tenant, lost.id())).isEmpty();
    }

    @Test
    void theSameKeyOfTwoTenantsIsTwoRecords() {
        String other = newId();
        String first = newId();
        String second = newId();

        keys.put(tenant, "shared-key-1", "CASH_MOVEMENT", first);
        keys.put(other, "shared-key-1", "CASH_MOVEMENT", second);

        assertThat(keys.find(tenant, "shared-key-1")).contains(new IdempotencyStore.Stored("CASH_MOVEMENT", first));
        assertThat(keys.find(other, "shared-key-1")).contains(new IdempotencyStore.Stored("CASH_MOVEMENT", second));
        assertThat(keys.find(newId(), "shared-key-1")).isEmpty();
        assertThat(jdbc.queryForObject(
                        "select count(*) from cash_reporting.idempotency_key where key = 'shared-key-1'"
                                + " and tenant_id in (?::uuid, ?::uuid)",
                        Integer.class,
                        tenant,
                        other))
                .isEqualTo(2);
        assertThatThrownBy(() -> keys.put(tenant, "shared-key-1", "CASH_MOVEMENT", newId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void aDayKeyReadsBackItsDate() {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));

        keys.put(tenant, "day-key-0001", "CASH_DAY", date.value().toString());

        assertThat(keys.find(tenant, "day-key-0001"))
                .contains(new IdempotencyStore.Stored("CASH_DAY", date.value().toString()));
        assertThat(jdbc.queryForObject(
                        "select resource_id::text from cash_reporting.idempotency_key where key = 'day-key-0001'"
                                + " and tenant_id = ?::uuid",
                        String.class,
                        tenant))
                .isEqualTo(rowOf(tenant, date).get("cash_session_id").toString());
    }

    @Test
    void twoTenantsHoldTheSameDateAndNoneSeesTheOther() {
        String other = newId();
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        sessions.save(other, opened(other, date, user));
        RecordedMovement mine = recorded(date, MovementType.INCOME, PaymentMedium.CASH, 10_000, user, OPENED_AT);
        RecordedMovement theirs = recorded(date, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 4_000, user, OPENED_AT);
        movements.insert(tenant, mine);
        movements.insert(other, theirs);

        assertThat(movements.list(tenant, date, new PageRequest(1, 20)).items())
                .extracting(RecordedMovement::id)
                .containsExactly(mine.id());
        assertThat(movements.list(other, date, new PageRequest(1, 20)).items())
                .extracting(RecordedMovement::id)
                .containsExactly(theirs.id());
        assertThat(movements.findByDate(tenant, date)).containsExactly(mine.movement());
        assertThat(movements.findByDates(other, List.of(date, day(4))))
                .containsEntry(date, List.of(theirs.movement()))
                .containsEntry(day(4), List.of());
        assertThat(movements.findById(tenant, theirs.id())).isEmpty();
        assertThat(movements.findById(other, mine.id())).isEmpty();
    }

    @Test
    void aMovementIsNeverUpdatedOrDeleted() throws IOException {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        RecordedMovement income = recorded(date, MovementType.INCOME, PaymentMedium.TRANSFER, 10_000, user, OPENED_AT);
        RecordedMovement refund = recorded(date, MovementType.REFUND, PaymentMedium.CASH, 3_000, user, OPENED_AT);

        movements.insert(tenant, income);
        movements.insert(tenant, refund);
        movements.list(tenant, date, new PageRequest(1, 20));
        movements.findById(tenant, income.id());

        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
        // the application user holds no UPDATE or DELETE on the tables (the -db grants), so the engine refuses them too
        assertThatThrownBy(() -> jdbc.update("update cash_reporting.cash_movement set amount_cents = 1"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from cash_reporting.refund_movement"))
                .isInstanceOf(DataAccessException.class);
        assertThat(count("cash_movement")).isEqualTo(2);
        assertThat(count("refund_movement")).isEqualTo(1);
        // and no statement that rewrites a movement exists in the adapter sources
        Path adapter = RepositoryRules.root()
                .resolve("cash-reporting-adapters/src/main/java/co/edu/corhuila/multi_tour/cash_reporting"
                        + "/adapter/out/persistence");
        try (Stream<Path> files = Files.list(adapter)) {
            var sources = files.filter(f -> f.getFileName().toString().contains("Movement"))
                    .filter(f -> f.toString().endsWith(".java"))
                    .toList();
            assertThat(sources).isNotEmpty();
            for (Path source : sources) {
                assertThat(Files.readString(source).toLowerCase())
                        .as(source.getFileName().toString())
                        .doesNotContain("update ", "delete ", ".remove(", ".merge(", "executeupdate");
            }
        }
    }

    @Test
    void aRefundRowReadsItsReservation() {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        RecordedMovement refund = recorded(date, MovementType.REFUND, PaymentMedium.CASH, 3_000, user, OPENED_AT);
        RecordedMovement income = recorded(date, MovementType.INCOME, PaymentMedium.TRANSFER, 8_000, user, OPENED_AT.plusSeconds(60));

        movements.insert(tenant, refund);
        movements.insert(tenant, income);

        Map<String, Object> detail = jdbc.queryForMap(
                "select * from cash_reporting.refund_movement where tenant_id = ?::uuid", tenant);
        assertThat(detail.get("reservation_id")).isEqualTo(UUID.fromString(refund.refund().reservationId()));
        assertThat(detail.get("amount_cents")).isEqualTo(3_000L);
        assertThat(detail.get("reason")).isEqualTo("Concept");
        assertThat(detail.get("outflow_method")).isEqualTo("CASH");
        assertThat(detail.get("executed_by")).isEqualTo(UUID.fromString(user));
        assertThat(count("refund_movement")).isEqualTo(1);

        var listed = movements.list(tenant, date, new PageRequest(1, 20));
        assertThat(listed.total()).isEqualTo(2);
        assertThat(listed.items()).extracting(RecordedMovement::id).containsExactly(income.id(), refund.id());
        RecordedMovement read = movements.findById(tenant, refund.id()).orElseThrow();
        assertThat(read).isEqualTo(refund);
        assertThat(listed.items().get(1)).isEqualTo(refund);
        assertThat(listed.items().get(0).refund()).isNull();
        assertThat(movements.findByDate(tenant, date))
                .containsExactlyInAnyOrder(refund.movement(), income.movement());
    }

    @Test
    void theOutboxHoldsTheNineEventNames() {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        String aggregate = newId();

        unitOfWork.inTransaction(() -> {
            for (String event : EVENTS) {
                outbox.append(tenant, event, aggregate, Map.of("businessDate", date.value().toString()));
            }
            return null;
        });

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select event_type, status, attempts, aggregate_id, payload::text as payload"
                        + " from cash_reporting.outbox_event where tenant_id = ?::uuid",
                tenant);
        assertThat(rows).extracting(row -> row.get("event_type")).containsExactlyInAnyOrderElementsOf(EVENTS);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("status")).isEqualTo("PENDING");
            assertThat(row.get("attempts")).isEqualTo(0);
            assertThat(row.get("aggregate_id")).isEqualTo(UUID.fromString(aggregate));
            assertThat((String) row.get("payload")).contains("businessDate", "2026-11-03");
        });
        // an event of a day names the cash of that day
        outbox.append(tenant, "CashSessionOpened", date.value().toString(), Map.of("businessDate", "2026-11-03"));
        assertThat(jdbc.queryForObject(
                        "select count(*) from cash_reporting.outbox_event where tenant_id = ?::uuid"
                                + " and aggregate_id = ?::uuid",
                        Integer.class,
                        tenant,
                        rowOf(tenant, date).get("cash_session_id").toString()))
                .isEqualTo(1);
        assertThatThrownBy(() -> outbox.append(tenant, "CashSessionDeleted", aggregate, Map.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void aMovementKeepsItsConceptWhateverItsType() {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        int minute = 0;
        for (MovementType type : MovementType.values()) {
            RecordedMovement movement = withConcept(
                    recorded(date, type, PaymentMedium.CASH, 1_000, user, OPENED_AT.plusSeconds(60L * minute++)),
                    "Concept of " + type);

            movements.insert(tenant, movement);

            assertThat(jdbc.queryForObject(
                            "select concept from cash_reporting.cash_movement where cash_movement_id = ?::uuid",
                            String.class,
                            movement.id()))
                    .isEqualTo("Concept of " + type);
            assertThat(movements.findById(tenant, movement.id()).orElseThrow().concept())
                    .isEqualTo("Concept of " + type);
        }
        assertThat(count("cash_movement")).isEqualTo(MovementType.values().length);
    }

    @Test
    void aRefundRowReadsItsConceptAndItsReason() {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        RecordedMovement refund = withConcept(
                recorded(date, MovementType.REFUND, PaymentMedium.CASH, 3_000, user, OPENED_AT), "Cancelled tour");

        movements.insert(tenant, refund);

        assertThat(jdbc.queryForObject(
                        "select concept from cash_reporting.cash_movement where cash_movement_id = ?::uuid",
                        String.class,
                        refund.id()))
                .isEqualTo("Cancelled tour");
        assertThat(jdbc.queryForObject(
                        "select reason from cash_reporting.refund_movement where cash_movement_id = ?::uuid",
                        String.class,
                        refund.id()))
                .isEqualTo("Cancelled tour");
        RecordedMovement read = movements.findById(tenant, refund.id()).orElseThrow();
        assertThat(read.concept()).isEqualTo("Cancelled tour");
        assertThat(read.refund().reservationId()).isEqualTo(refund.refund().reservationId());
        assertThat(read).isEqualTo(refund);
    }

    @Test
    void theConceptSurvivesTheListAndTheDayBatch() {
        var date = day(3);
        sessions.save(tenant, opened(tenant, date, user));
        RecordedMovement income = withConcept(
                recorded(date, MovementType.INCOME, PaymentMedium.TRANSFER, 8_000, user, OPENED_AT), "Deposit");
        RecordedMovement expense = withConcept(
                recorded(date, MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 2_000, user, OPENED_AT.plusSeconds(60)),
                "Fuel");
        RecordedMovement refund = withConcept(
                recorded(date, MovementType.REFUND, PaymentMedium.CASH, 1_000, user, OPENED_AT.plusSeconds(120)), "Refund");
        movements.insert(tenant, income);
        movements.insert(tenant, expense);
        movements.insert(tenant, refund);

        assertThat(movements.list(tenant, date, new PageRequest(1, 20)).items())
                .containsExactly(refund, expense, income);
        assertThat(movements.findByDates(tenant, List.of(date)).get(date))
                .containsExactly(income.movement(), expense.movement(), refund.movement());
        assertThat(movements.findByDates(tenant, List.of(date)).get(date)).hasSize(3);
    }

    private static RecordedMovement withConcept(RecordedMovement movement, String concept) {
        return new RecordedMovement(
                movement.id(),
                movement.date(),
                movement.movement(),
                concept,
                movement.afterClosing(),
                movement.registeredAt(),
                movement.registeredBy(),
                movement.refund());
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "select count(*) from cash_reporting." + table + " where tenant_id = ?::uuid", Integer.class, tenant);
    }
}
