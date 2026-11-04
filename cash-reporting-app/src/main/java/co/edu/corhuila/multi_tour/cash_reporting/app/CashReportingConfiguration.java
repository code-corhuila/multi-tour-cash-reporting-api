package co.edu.corhuila.multi_tour.cash_reporting.app;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CashReportingConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
