package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryCashSessionRepository;
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
}
