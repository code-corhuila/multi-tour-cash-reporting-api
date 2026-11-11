package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.PendingClosureAlert;
import java.util.ArrayList;
import java.util.List;

/** Test double of the alert port: it keeps what was raised. */
public final class InMemoryPendingClosureAlert implements PendingClosureAlert {

    public record Raised(String tenantId, BusinessDate date) {}

    private final List<Raised> raised = new ArrayList<>();

    public List<Raised> raised() {
        return raised;
    }

    @Override
    public void raise(String tenantId, BusinessDate date) {
        raised.add(new Raised(tenantId, date));
    }
}
