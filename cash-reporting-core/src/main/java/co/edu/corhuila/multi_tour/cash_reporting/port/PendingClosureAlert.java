package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;

/**
 * Outbound port of the alert for a cash with operational opening that was not closed when its day ended: it goes to the
 * active Administrators of the tenant and to the users with the permission Gestionar caja (PDR 1.9, GAP-012). Until the
 * notification piece exists the adapter only records that it was raised.
 */
public interface PendingClosureAlert {

    void raise(String tenantId, BusinessDate date);
}
