package co.edu.corhuila.multi_tour.cash_reporting.port;

/**
 * Outbound port: the ids of the events already consumed (table {@code processed_event}, unique per tenant and event). It is
 * written in the same transaction as the effect of the event (ADR-004 D10).
 */
public interface ProcessedEvents {

    /** Records the event and answers true when it is new, false when the tenant already consumed it. */
    boolean firstTime(String tenantId, String eventId, String eventType);
}
