package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The correction notes in PostgreSQL: insert-only (the writer has no UPDATE or DELETE). The date comes from the day. */
@Component
@Transactional
public class JpaCashCorrectionRepository implements CashCorrectionRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public CashCorrection insert(String tenantId, CashCorrection correction) {
        var row = new CashClosureCorrectionEntity();
        row.id = UUID.fromString(correction.id());
        row.tenantId = UUID.fromString(tenantId);
        row.sessionId = JpaCashSessionRepository.idOf(em, tenantId, correction.date());
        row.closureId = UUID.fromString(correction.closureId());
        row.justification = correction.justification();
        row.registeredBy = UUID.fromString(correction.registeredBy());
        row.registeredAt = correction.registeredAt();
        em.persist(row);
        em.flush();
        return correction;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashCorrection> find(String tenantId, String correctionId) {
        return em.createQuery(
                        "select c, s.businessDate from CashClosureCorrectionEntity c join CashSessionEntity s"
                                + " on s.id = c.sessionId and s.tenantId = c.tenantId"
                                + " where c.tenantId = :tenant and c.id = :id",
                        Object[].class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("id", UUID.fromString(correctionId))
                .getResultStream()
                .findFirst()
                .map(JpaCashCorrectionRepository::toDomain);
    }

    /** One read for all the closures of the page. */
    @Override
    @Transactional(readOnly = true)
    public List<CashCorrection> listByClosures(String tenantId, List<String> closureIds) {
        if (closureIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select c, s.businessDate from CashClosureCorrectionEntity c join CashSessionEntity s"
                                + " on s.id = c.sessionId and s.tenantId = c.tenantId"
                                + " where c.tenantId = :tenant and c.closureId in :ids order by c.registeredAt, c.id",
                        Object[].class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("ids", closureIds.stream().map(UUID::fromString).toList())
                .getResultList()
                .stream()
                .map(JpaCashCorrectionRepository::toDomain)
                .toList();
    }

    private static CashCorrection toDomain(Object[] pair) {
        var c = (CashClosureCorrectionEntity) pair[0];
        return new CashCorrection(
                c.id.toString(),
                c.tenantId.toString(),
                BusinessDate.of((LocalDate) pair[1]),
                c.closureId.toString(),
                c.justification,
                c.registeredBy.toString(),
                c.registeredAt);
    }
}
