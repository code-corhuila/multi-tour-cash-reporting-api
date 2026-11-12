package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureStatus;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.port.DashboardFacts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The last known state of the reservations and departures in PostgreSQL: one row per tenant and id, the tenant first. */
@Component
@Transactional
public class JpaDashboardFacts implements DashboardFacts {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationFact> findReservation(String tenantId, String reservationId) {
        return row(tenantId, reservationId).map(JpaDashboardFacts::toFact);
    }

    @Override
    public void saveReservation(String tenantId, ReservationFact fact) {
        ReservationStateEntity row = row(tenantId, fact.reservationId()).orElseGet(() -> {
            var created = new ReservationStateEntity();
            created.id = UUID.randomUUID();
            created.tenantId = UUID.fromString(tenantId);
            created.reservationId = fact.reservationId();
            return created;
        });
        row.status = fact.status() == null ? "UNKNOWN" : fact.status().name();
        row.financialSituation = fact.financialSituation();
        row.createdAt = fact.createdAt();
        row.lastEventOccurredAt = fact.lastEventOccurredAt();
        row.lastEventId = fact.lastEventId();
        row.updatedAt = Instant.now();
        em.persist(row);
        em.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationFact> reservations(String tenantId) {
        return em.createQuery("select r from ReservationStateEntity r where r.tenantId = :tenant", ReservationStateEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .getResultList()
                .stream()
                .map(JpaDashboardFacts::toFact)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DepartureFact> findDeparture(String tenantId, String departureId) {
        return departureRow(tenantId, departureId).map(JpaDashboardFacts::toFact);
    }

    @Override
    public void saveDeparture(String tenantId, DepartureFact fact) {
        DepartureStateEntity row = departureRow(tenantId, fact.departureId()).orElseGet(() -> {
            var created = new DepartureStateEntity();
            created.id = UUID.randomUUID();
            created.tenantId = UUID.fromString(tenantId);
            created.departureId = fact.departureId();
            return created;
        });
        row.status = fact.status() == null ? "UNKNOWN" : fact.status().name();
        row.scheduledStart = fact.scheduledStart();
        row.lastEventOccurredAt = fact.lastEventOccurredAt();
        row.lastEventId = fact.lastEventId();
        row.updatedAt = Instant.now();
        em.persist(row);
        em.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartureFact> departures(String tenantId) {
        return em.createQuery("select d from DepartureStateEntity d where d.tenantId = :tenant", DepartureStateEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .getResultList()
                .stream()
                .map(JpaDashboardFacts::toFact)
                .toList();
    }

    private Optional<ReservationStateEntity> row(String tenantId, String reservationId) {
        return em.createQuery(
                        "select r from ReservationStateEntity r where r.tenantId = :tenant and r.reservationId = :id",
                        ReservationStateEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("id", reservationId)
                .getResultStream()
                .findFirst();
    }

    private Optional<DepartureStateEntity> departureRow(String tenantId, String departureId) {
        return em.createQuery(
                        "select d from DepartureStateEntity d where d.tenantId = :tenant and d.departureId = :id",
                        DepartureStateEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("id", departureId)
                .getResultStream()
                .findFirst();
    }

    private static ReservationFact toFact(ReservationStateEntity row) {
        ReservationStatus status = "UNKNOWN".equals(row.status) ? null : ReservationStatus.valueOf(row.status);
        return new ReservationFact(
                row.reservationId, status, row.financialSituation, row.createdAt, row.lastEventOccurredAt, row.lastEventId);
    }

    private static DepartureFact toFact(DepartureStateEntity row) {
        DepartureStatus status = "UNKNOWN".equals(row.status) ? null : DepartureStatus.valueOf(row.status);
        return new DepartureFact(row.departureId, status, row.scheduledStart, row.lastEventOccurredAt, row.lastEventId);
    }
}
