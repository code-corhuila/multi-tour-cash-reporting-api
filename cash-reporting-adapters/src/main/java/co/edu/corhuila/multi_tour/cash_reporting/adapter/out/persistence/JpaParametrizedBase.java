package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.ParametrizedBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** DP-CJ1: the base of the latest operational opening of the tenant, adjustments included, or zero when there is none. */
@Component
public class JpaParametrizedBase implements ParametrizedBase {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Cents forTenant(String tenantId) {
        return em.createQuery("select s.openingBaseCents from CashSessionEntity s"
                        + " where s.tenantId = :tenant and s.openedAt is not null"
                        + " order by s.openedAt desc, s.businessDate desc", Long.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(Cents::of)
                .orElse(Cents.of(0));
    }
}
