package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The movements in PostgreSQL: insert-only. A REFUND row reads amount, reason and reservation through the detail table.
 * The movement table holds no concept, so the concept of the other types is not stored.
 */
@Component
@Transactional
public class JpaCashMovementRepository implements CashMovementRepository {

    private static final String FROM = " from CashMovementEntity m join CashSessionEntity s"
            + " on s.id = m.sessionId and s.tenantId = m.tenantId"
            + " left join RefundMovementEntity r on r.movementId = m.id and r.tenantId = m.tenantId";

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public List<CashMovement> findByDate(String tenantId, BusinessDate date) {
        return findByDates(tenantId, List.of(date)).get(date);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<BusinessDate, List<CashMovement>> findByDates(String tenantId, List<BusinessDate> dates) {
        Map<BusinessDate, List<CashMovement>> byDate = new LinkedHashMap<>();
        dates.forEach(date -> byDate.put(date, new ArrayList<>()));
        if (dates.isEmpty()) {
            return byDate;
        }
        em.createQuery(
                        "select m, r, s.businessDate" + FROM + " where m.tenantId = :tenant and s.businessDate in :dates"
                                + " order by m.registeredAt, m.id",
                        Object[].class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("dates", dates.stream().map(BusinessDate::value).toList())
                .getResultList()
                .forEach(row -> byDate.get(BusinessDate.of((LocalDate) row[2]))
                        .add(toDomain(row).movement()));
        return byDate;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RecordedMovement> list(String tenantId, BusinessDate date, PageRequest request) {
        UUID tenant = UUID.fromString(tenantId);
        long total = em.createQuery(
                        "select count(m)" + FROM + " where m.tenantId = :tenant and s.businessDate = :date", Long.class)
                .setParameter("tenant", tenant)
                .setParameter("date", date.value())
                .getSingleResult();
        List<RecordedMovement> items = em.createQuery(
                        "select m, r, s.businessDate" + FROM + " where m.tenantId = :tenant and s.businessDate = :date"
                                + " order by m.registeredAt desc, m.id desc",
                        Object[].class)
                .setParameter("tenant", tenant)
                .setParameter("date", date.value())
                .setFirstResult(request.offset())
                .setMaxResults(request.limit())
                .getResultStream()
                .map(JpaCashMovementRepository::toDomain)
                .toList();
        return new Page<>(items, request.page(), request.limit(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordedMovement> findById(String tenantId, String id) {
        return em.createQuery(
                        "select m, r, s.businessDate" + FROM + " where m.tenantId = :tenant and m.id = :id",
                        Object[].class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("id", UUID.fromString(id))
                .getResultStream()
                .findFirst()
                .map(JpaCashMovementRepository::toDomain);
    }

    /** Writes the movement and, for a REFUND, its detail row. The day must exist: the use case saved it first. */
    @Override
    public RecordedMovement insert(String tenantId, RecordedMovement movement) {
        UUID sessionId = JpaCashSessionRepository.idOf(em, tenantId, movement.date());
        var row = new CashMovementEntity();
        row.id = UUID.fromString(movement.id());
        row.tenantId = UUID.fromString(tenantId);
        row.sessionId = sessionId;
        row.type = movement.movement().type().name();
        row.medium = movement.movement().medium().name();
        row.amountCents = movement.movement().amount().value();
        row.afterClosing = movement.afterClosing();
        row.registeredAt = movement.registeredAt();
        row.registeredBy = UUID.fromString(movement.registeredBy());
        em.persist(row);
        em.flush();
        if (movement.refund() != null) {
            var detail = new RefundMovementEntity();
            detail.id = UUID.randomUUID();
            detail.tenantId = row.tenantId;
            detail.sessionId = sessionId;
            detail.movementId = row.id;
            // The domain carries no authorization yet: the refund names itself until the saga supplies it.
            detail.authorizationId = row.id;
            detail.reservationId = UUID.fromString(movement.refund().reservationId());
            detail.amountCents = row.amountCents;
            detail.reason = movement.concept();
            detail.executedBy = row.registeredBy;
            detail.outflowMethod = row.medium;
            detail.executedAt = row.registeredAt;
            em.persist(detail);
            em.flush();
        }
        return movement;
    }

    private static RecordedMovement toDomain(Object[] row) {
        var m = (CashMovementEntity) row[0];
        var r = (RefundMovementEntity) row[1];
        MovementType type = MovementType.valueOf(m.type);
        if ((type == MovementType.REFUND) != (r != null)) {
            throw new IllegalStateException("A REFUND movement and its detail row go together: " + m.id);
        }
        return new RecordedMovement(
                m.id.toString(),
                BusinessDate.of((LocalDate) row[2]),
                new CashMovement(type, PaymentMedium.valueOf(m.medium), Cents.amount(r == null ? m.amountCents : r.amountCents)),
                r == null ? "" : r.reason,
                m.afterClosing,
                m.registeredAt,
                m.registeredBy.toString(),
                r == null ? null : new RefundDetail(r.reservationId.toString()));
    }
}
