package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryIdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryOutbox;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryUnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.port.ParametrizedBase;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * C level world (ADR-004 D7): the repositories are the in-memory fakes of the core tests, shared as a test-jar, and the
 * clock is fixed. The tests of this level never touch a database; each test uses a tenant of its own.
 */
@TestConfiguration
class InMemoryWorld {

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(TestKeys.NOW, ZoneOffset.UTC);
    }

    @Bean
    @Primary
    InMemoryCashSessionRepository inMemorySessions() {
        return new InMemoryCashSessionRepository();
    }

    @Bean
    @Primary
    InMemoryCashMovementRepository inMemoryMovements() {
        return new InMemoryCashMovementRepository();
    }

    @Bean
    @Primary
    InMemoryCashClosureRepository inMemoryClosures() {
        return new InMemoryCashClosureRepository();
    }

    @Bean
    @Primary
    InMemoryCashReopeningRepository inMemoryReopenings() {
        return new InMemoryCashReopeningRepository();
    }

    @Bean
    @Primary
    InMemoryCashCorrectionRepository inMemoryCorrections() {
        return new InMemoryCashCorrectionRepository();
    }

    @Bean
    @Primary
    InMemoryIdempotencyStore inMemoryKeys() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @Primary
    InMemoryOutbox inMemoryOutbox() {
        return new InMemoryOutbox();
    }

    @Bean
    @Primary
    InMemoryUnitOfWork inMemoryUnitOfWork() {
        return new InMemoryUnitOfWork();
    }

    /** Same rule as the JPA read: the base of the latest operational opening of the tenant, zero when there is none. */
    @Bean
    @Primary
    ParametrizedBase inMemoryParametrizedBase(InMemoryCashSessionRepository sessions) {
        return tenantId -> sessions.list(tenantId, new PageRequest(1, PageRequest.MAX_LIMIT), null).items().stream()
                .filter(CashSession::hasOperationalOpening)
                .findFirst()
                .map(session -> session.opening().base())
                .orElse(Cents.of(0));
    }
}
