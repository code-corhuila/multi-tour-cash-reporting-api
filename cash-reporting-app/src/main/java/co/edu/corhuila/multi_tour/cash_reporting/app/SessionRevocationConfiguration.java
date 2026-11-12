package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.port.SessionCutoffs;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ApplySessionRevocation;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.IsTokenRevoked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The use cases of the revocation of the sessions (HU-SEC-005 AC4, HU-SEC-006 AC7), built with the port of the cutoffs. */
@Configuration
class SessionRevocationConfiguration {

    @Bean
    ApplySessionRevocation applySessionRevocation(SessionCutoffs cutoffs) {
        return new ApplySessionRevocation(cutoffs);
    }

    @Bean
    IsTokenRevoked isTokenRevoked(SessionCutoffs cutoffs) {
        return new IsTokenRevoked(cutoffs);
    }
}
