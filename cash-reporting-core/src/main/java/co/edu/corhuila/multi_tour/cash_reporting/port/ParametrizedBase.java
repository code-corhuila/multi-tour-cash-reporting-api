package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;

/**
 * Outbound port: the base a Collaborator opens with. PROPUESTO (DP-CJ1): the base of the latest operational opening of
 * the tenant, adjustments included, or zero when there is none.
 */
@FunctionalInterface
public interface ParametrizedBase {

    Cents forTenant(String tenantId);
}
