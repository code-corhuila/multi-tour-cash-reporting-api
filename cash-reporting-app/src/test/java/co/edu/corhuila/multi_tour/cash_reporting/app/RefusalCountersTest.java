package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Plan section 5: a counter per operation and outcome, a counter per refusal rule, and no tenant or data in any label. */
class RefusalCountersTest extends PrivacyRun {

    private static final String OPEN = "POST /api/v1/cash-reporting/cash-days/{businessDate}/open";
    private static final String MOVEMENTS = "POST /api/v1/cash-reporting/cash-days/{businessDate}/movements";
    private static final List<String> RULES =
            List.of("no-open-cash", "already-open", "already-closed", "forbidden", "pending-closure");

    @Autowired
    MeterRegistry meters;

    @Test
    void theRefusalCountersAreIncremented() throws Exception {
        // the registry is shared with the other tests of the same context: only the change made by this run counts
        Map<String, Double> before = RULES.stream().collect(Collectors.toMap(rule -> rule, this::refusals));
        double reopened = operations(OPEN, "ALREADY_OPEN");
        double registered = operations(MOVEMENTS, "OK");

        run();

        assertThat(refusals("no-open-cash") - before.get("no-open-cash")).isEqualTo(1);
        assertThat(refusals("already-open") - before.get("already-open")).isEqualTo(1);
        assertThat(refusals("already-closed") - before.get("already-closed")).isEqualTo(1);
        assertThat(refusals("forbidden") - before.get("forbidden")).isEqualTo(1);
        assertThat(refusals("pending-closure") - before.get("pending-closure")).isZero();
        assertThat(operations(OPEN, "ALREADY_OPEN") - reopened).isEqualTo(1);
        assertThat(operations(MOVEMENTS, "OK") - registered).isEqualTo(2);
        assertThat(meters.find("cash_operation_seconds").timers()).isNotEmpty();
    }

    @Test
    void noMeterCarriesATenantOrAnyDataOfTheRequest() throws Exception {
        run();

        List<Meter> cash = meters.getMeters().stream().filter(meter -> meter.getId().getName().startsWith("cash_")).toList();
        assertThat(cash).isNotEmpty();
        assertThat(cash.stream().flatMap(meter -> meter.getId().getTags().stream()).map(tag -> tag.getKey())
                .collect(Collectors.toSet())).isSubsetOf("operation", "outcome", "rule");
        String all = cash.stream().map(meter -> meter.getId().toString()).collect(Collectors.joining("\n"));
        assertThat(all).doesNotContain(tenant, CONCEPT, REASON, AMOUNT_CENTS, BASE_CENTS);
    }

    private double refusals(String rule) {
        var counter = meters.find("cash_refusal_total").tag("rule", rule).counter();
        return counter == null ? 0 : counter.count();
    }

    private double operations(String operation, String outcome) {
        var counter = meters.find("cash_operation_total").tags("operation", operation, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
