package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Public on purpose (no token); a database that does not answer is 503.
@RestController
public class HealthController {
    private final Clock clock;
    private final DatabaseProbe database;

    public HealthController(Clock clock, DatabaseProbe database) { this.clock = clock; this.database = database; }

    @GetMapping("/health")
    ResponseEntity<HealthResponse> health() {
        if (!database.up()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build(); // the contract allows no body here
        }
        return ResponseEntity.ok(new HealthResponse("ok", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString()));
    }
}
