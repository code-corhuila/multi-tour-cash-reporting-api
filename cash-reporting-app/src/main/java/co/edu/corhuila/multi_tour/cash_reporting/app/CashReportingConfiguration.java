package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.DatabaseProbe;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.Rs256TokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class CashReportingConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Rs256TokenVerifier rs256TokenVerifier(Environment env, Clock clock, ObjectMapper json) {
        return new Rs256TokenVerifier(
                Rs256TokenVerifier.loadKey(env.getProperty("JWT_PUBLIC_KEY"), env.getProperty("JWT_PUBLIC_KEY_FILE")), clock, json);
    }

    // The string of "Gestionar caja" is NO DEFINIDO (G-PERM HU-SEC-006): configuration only, a blank value stops the start.
    @Bean
    CashPermissions cashPermissions(@Value("${cash.permission.manage-cash:}") String manageCash) {
        return new CashPermissions(manageCash);
    }

    @Bean
    DatabaseProbe databaseProbe(DataSource dataSource) {
        return () -> {
            try (var connection = dataSource.getConnection()) {
                return connection.isValid(2);
            } catch (Exception failure) {
                return false;
            }
        };
    }
}
