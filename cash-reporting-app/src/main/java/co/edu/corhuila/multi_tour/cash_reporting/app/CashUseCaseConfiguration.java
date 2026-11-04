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
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashClosures;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashDays;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashMovements;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root: the only place that builds the use cases of the core (which knows no Spring) with the adapters.
 * The use cases of the writes come with their controllers. RegisterCashCorrection is not built at all: the corrections
 * have no storage (DP-D1-2) and the operation is not exposed.
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

    // Fails closed: nothing can be written (500, never a silent loss); the history of closures lists no corrections.
    @Bean
    CashCorrectionRepository cashCorrectionRepository() {
        return new CashCorrectionRepository() {
            @Override
            public CashCorrection insert(String tenantId, CashCorrection correction) {
                throw new IllegalStateException("The corrections have no storage yet (DP-D1-2)");
            }

            @Override
            public Optional<CashCorrection> find(String tenantId, String correctionId) {
                return Optional.empty();
            }

            @Override
            public List<CashCorrection> listByClosures(String tenantId, List<String> closureIds) {
                return List.of();
            }
        };
    }

    @Bean
    GetCashDay getCashDay(CashPermissions permissions, CashSessionRepository sessions, CashMovementRepository movements,
            CashClosureRepository closures, TenantCalendar calendar) {
        return new GetCashDay(permissions, sessions, movements, closures, calendar);
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
}
