package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The reopenings in PostgreSQL: insert-only. The date comes from the day the reopening points at. */
@Component
@Transactional
public class JpaCashReopeningRepository implements CashReopeningRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public CashReopening insert(String tenantId, CashReopening reopening) {
        var row = new CashReopeningEntity();
        row.id = UUID.fromString(reopening.id());
        row.tenantId = UUID.fromString(tenantId);
        row.sessionId = JpaCashSessionRepository.idOf(em, tenantId, reopening.date());
        row.closureId = UUID.fromString(reopening.closureId());
        row.reopenedBy = UUID.fromString(reopening.reopenedBy());
        row.reopenedAt = reopening.reopenedAt();
        row.reason = reopening.reason();
        em.persist(row);
        em.flush();
        return reopening;
    }

    /** One read for all the closures of the page. */
    @Override
    @Transactional(readOnly = true)
    public List<CashReopening> listByClosures(String tenantId, List<String> closureIds) {
        if (closureIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select r, s.businessDate from CashReopeningEntity r join CashSessionEntity s"
                                + " on s.id = r.sessionId and s.tenantId = r.tenantId"
                                + " where r.tenantId = :tenant and r.closureId in :ids order by r.reopenedAt, r.id",
                        Object[].class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("ids", closureIds.stream().map(UUID::fromString).toList())
                .getResultList()
                .stream()
                .map(pair -> {
                    var r = (CashReopeningEntity) pair[0];
                    return new CashReopening(
                            r.id.toString(),
                            r.tenantId.toString(),
                            BusinessDate.of((LocalDate) pair[1]),
                            r.closureId.toString(),
                            r.reason,
                            r.reopenedBy.toString(),
                            r.reopenedAt);
                })
                .toList();
    }
}
