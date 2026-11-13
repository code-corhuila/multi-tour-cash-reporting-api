package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.port.SessionCutoffs;
import co.edu.corhuila.multi_tour.cash_reporting.revocation.SessionCutoff;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cutoff of each account in PostgreSQL (table account_session_cutoff of the -db): one row per account. The write is one
 * upsert that moves the instant only forward, so two events that race cannot leave an older cutoff behind.
 */
@Component
@Transactional
public class JpaSessionCutoffs implements SessionCutoffs {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionCutoff> find(String accountId) {
        List<?> rows = em.createNativeQuery("select tenant_id, session_cutoff_at, event_id"
                        + " from cash_reporting.account_session_cutoff where account_id = ?1")
                .setParameter(1, UUID.fromString(accountId))
                .getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) rows.get(0);
        return Optional.of(new SessionCutoff(accountId, row[0] == null ? null : row[0].toString(),
                instantOf(row[1]), row[2].toString()));
    }

    private static Instant instantOf(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return ((Timestamp) value).toInstant();
    }

    @Override
    public boolean advance(SessionCutoff cutoff) {
        int written = em.createNativeQuery("insert into cash_reporting.account_session_cutoff"
                        + " (account_id, tenant_id, session_cutoff_at, event_id, updated_at) values (?1, cast(?2 as uuid), ?3, ?4, now())"
                        + " on conflict (account_id) do update set tenant_id = excluded.tenant_id,"
                        + " session_cutoff_at = excluded.session_cutoff_at, event_id = excluded.event_id, updated_at = now()"
                        + " where excluded.session_cutoff_at > cash_reporting.account_session_cutoff.session_cutoff_at")
                .setParameter(1, UUID.fromString(cutoff.accountId()))
                .setParameter(2, cutoff.tenantId())
                .setParameter(3, Timestamp.from(cutoff.revokedFrom()))
                .setParameter(4, UUID.fromString(cutoff.eventId()))
                .executeUpdate();
        return written == 1;
    }
}
