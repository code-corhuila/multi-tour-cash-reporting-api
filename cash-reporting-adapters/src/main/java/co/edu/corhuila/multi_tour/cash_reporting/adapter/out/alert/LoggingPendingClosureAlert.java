package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.alert;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.PendingClosureAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Until the notification piece exists the alert is only recorded in the log, with ids and the date (no names). */
@Component
public class LoggingPendingClosureAlert implements PendingClosureAlert {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPendingClosureAlert.class);

    @Override
    public void raise(String tenantId, BusinessDate date) {
        LOG.warn("pending closure alert raised tenant={} businessDate={}", tenantId, date.value());
    }
}
