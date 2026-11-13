package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantZones;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.ZonedTenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.DashboardFacts;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxDelivery;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import co.edu.corhuila.multi_tour.cash_reporting.port.PendingClosureAlert;
import co.edu.corhuila.multi_tour.cash_reporting.port.ParametrizedBase;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.AdjustCashBase;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.AutoCloseCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.CloseCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListOpenCashDays;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RecordExecutedRefund;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RelayOutbox;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RecordSagaIncome;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ReverseCashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ConsumeDepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ConsumeReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ReceiveFactEvent;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetDailyDashboard;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashClosures;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashDays;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashMovements;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.OpenCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RegisterCashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RegisterCashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ReopenCashDay;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root: the only place that builds the use cases of the core (which knows no Spring) with the adapters.
 * The writes are composed here as their controllers arrive, the correction included (storage: DP-D1-2, table cash_closure_correction).
 */
@Configuration
class CashUseCaseConfiguration {

    @Bean
    Clock cashClock(java.time.Clock clock) {
        return clock::instant;
    }

    // The zone of each tenant is NO DEFINIDO (H-06): one configured zone for every tenant until it is closed.
    @Bean
    TenantZones tenantZones(@Value("${cash.default-time-zone:America/Bogota}") String zone) {
        ZoneId id = ZoneId.of(zone);
        return tenantId -> id;
    }

    @Bean
    TenantCalendar tenantCalendar(Clock clock, TenantZones zones) {
        return new ZonedTenantCalendar(clock, zones);
    }

    @Bean
    ConsumeReservationEvent consumeReservationEvent(ProcessedEvents processed, DashboardFacts facts, UnitOfWork unitOfWork) {
        return new ConsumeReservationEvent(processed, facts, unitOfWork);
    }

    @Bean
    ConsumeDepartureEvent consumeDepartureEvent(ProcessedEvents processed, DashboardFacts facts, UnitOfWork unitOfWork) {
        return new ConsumeDepartureEvent(processed, facts, unitOfWork);
    }

    @Bean
    ReceiveFactEvent receiveFactEvent(ConsumeReservationEvent reservations, ConsumeDepartureEvent departures) {
        return new ReceiveFactEvent(reservations, departures);
    }

    @Bean
    GetDailyDashboard getDailyDashboard(CashPermissions permissions, DashboardFacts facts, TenantCalendar calendar) {
        return new GetDailyDashboard(permissions, facts, calendar);
    }

    @Bean
    GetCashDay getCashDay(CashPermissions permissions, CashSessionRepository sessions, CashMovementRepository movements,
            CashClosureRepository closures, TenantCalendar calendar, ParametrizedBase parametrizedBase) {
        return new GetCashDay(permissions, sessions, movements, closures, calendar, parametrizedBase);
    }

    @Bean
    ListCashDays listCashDays(CashPermissions permissions, CashSessionRepository sessions,
            CashMovementRepository movements, CashClosureRepository closures, TenantCalendar calendar) {
        return new ListCashDays(permissions, sessions, movements, closures, calendar);
    }

    @Bean
    ListCashMovements listCashMovements(CashPermissions permissions, CashMovementRepository movements,
            TenantCalendar calendar) {
        return new ListCashMovements(permissions, movements, calendar);
    }

    @Bean
    ListCashClosures listCashClosures(CashPermissions permissions, CashClosureRepository closures,
            CashReopeningRepository reopenings, CashCorrectionRepository corrections) {
        return new ListCashClosures(permissions, closures, reopenings, corrections);
    }

    @Bean
    OpenCashDay openCashDay(CashPermissions permissions, CashSessionRepository sessions, ParametrizedBase base,
            IdempotencyStore keys, OutboxWriter outbox, TenantCalendar calendar, Clock clock, UnitOfWork unitOfWork) {
        return new OpenCashDay(permissions, sessions, base, keys, outbox, calendar, clock, unitOfWork);
    }

    @Bean
    RegisterCashCorrection registerCashCorrection(CashPermissions permissions, CashSessionRepository sessions,
            CashClosureRepository closures, CashCorrectionRepository corrections, IdempotencyStore keys, Clock clock,
            IdGenerator ids, UnitOfWork unitOfWork) {
        return new RegisterCashCorrection(permissions, sessions, closures, corrections, keys, clock, ids, unitOfWork);
    }

    @Bean
    RegisterCashMovement registerCashMovement(CashPermissions permissions, CashSessionRepository sessions,
            CashMovementRepository movements, IdempotencyStore keys, OutboxWriter outbox, TenantCalendar calendar,
            Clock clock, IdGenerator ids, UnitOfWork unitOfWork) {
        return new RegisterCashMovement(permissions, sessions, movements, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    @Bean
    RecordSagaIncome recordSagaIncome(CashSessionRepository sessions, CashMovementRepository movements,
            IdempotencyStore keys, OutboxWriter outbox, TenantCalendar calendar, Clock clock, IdGenerator ids,
            UnitOfWork unitOfWork) {
        return new RecordSagaIncome(sessions, movements, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    @Bean
    ReverseCashMovement reverseCashMovement(CashSessionRepository sessions, CashMovementRepository movements,
            IdempotencyStore keys, OutboxWriter outbox, TenantCalendar calendar, Clock clock, IdGenerator ids,
            UnitOfWork unitOfWork) {
        return new ReverseCashMovement(sessions, movements, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    @Bean
    RecordExecutedRefund recordExecutedRefund(CashSessionRepository sessions, CashMovementRepository movements,
            IdempotencyStore keys, OutboxWriter outbox, TenantCalendar calendar, Clock clock, IdGenerator ids,
            UnitOfWork unitOfWork) {
        return new RecordExecutedRefund(sessions, movements, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    @Bean
    AutoCloseCashDay autoCloseCashDay(CashSessionRepository sessions, CashMovementRepository movements,
            CashClosureRepository closures, OutboxWriter outbox, PendingClosureAlert alerts, Clock clock,
            IdGenerator ids, UnitOfWork unitOfWork) {
        return new AutoCloseCashDay(sessions, movements, closures, outbox, alerts, clock, ids, unitOfWork);
    }

    @Bean
    RelayOutbox relayOutbox(OutboxDelivery outbox, Clock clock) {
        return new RelayOutbox(outbox, clock);
    }

    @Bean
    ListOpenCashDays listOpenCashDays(CashSessionRepository sessions, TenantCalendar calendar) {
        return new ListOpenCashDays(sessions, calendar);
    }

    @Bean
    CloseCashDay closeCashDay(CashPermissions permissions, CashSessionRepository sessions,
            CashMovementRepository movements, CashClosureRepository closures, IdempotencyStore keys, OutboxWriter outbox,
            TenantCalendar calendar, Clock clock, IdGenerator ids, UnitOfWork unitOfWork) {
        return new CloseCashDay(permissions, sessions, movements, closures, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    @Bean
    ReopenCashDay reopenCashDay(CashPermissions permissions, CashSessionRepository sessions,
            CashClosureRepository closures, CashReopeningRepository reopenings, IdempotencyStore keys,
            OutboxWriter outbox, Clock clock, IdGenerator ids, UnitOfWork unitOfWork) {
        return new ReopenCashDay(permissions, sessions, closures, reopenings, keys, outbox, clock, ids, unitOfWork);
    }

    @Bean
    AdjustCashBase adjustCashBase(CashPermissions permissions, CashSessionRepository sessions, TenantCalendar calendar,
            UnitOfWork unitOfWork) {
        return new AdjustCashBase(permissions, sessions, calendar, unitOfWork);
    }
}
