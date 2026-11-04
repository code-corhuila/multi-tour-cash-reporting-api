package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashErrorMapping.Code;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * The counters and the timer of plan section 5. Labels are the route pattern, the outcome and the refusal rule: never a
 * tenant, an id, a user, a concept or an amount.
 */
@Component
public class CashMetrics {

    private final MeterRegistry registry;

    public CashMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void operation(String operation, String outcome, long nanos) {
        registry.counter("cash_operation_total", "operation", operation, "outcome", outcome).increment();
        registry.timer("cash_operation_seconds", "operation", operation).record(nanos, TimeUnit.NANOSECONDS);
    }

    void refusal(Code code) {
        rule(code).ifPresent(rule -> registry.counter("cash_refusal_total", "rule", rule).increment());
    }

    private static Optional<String> rule(Code code) {
        return switch (code) {
            case NO_OPEN_CASH -> Optional.of("no-open-cash");
            case ALREADY_CLOSED -> Optional.of("already-closed");
            case ALREADY_OPEN -> Optional.of("already-open");
            case PENDING_CLOSURE -> Optional.of("pending-closure");
            case FORBIDDEN -> Optional.of("forbidden");
            default -> Optional.empty();
        };
    }
}
