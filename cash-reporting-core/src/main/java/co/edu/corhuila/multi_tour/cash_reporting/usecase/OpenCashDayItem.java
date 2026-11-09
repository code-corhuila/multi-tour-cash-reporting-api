package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import java.time.ZoneId;

/** One open cash of the sweep of job 8: what the worker needs to decide, with the tenant it belongs to. */
public record OpenCashDayItem(
        String tenantId,
        BusinessDate date,
        ZoneId zone,
        Origin origin,
        boolean hasOperationalOpening,
        boolean pendingClosure) {}
