package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.util.function.Supplier;

/** Outbound port: one transaction for the rows, the idempotency key and the outbox events (RN-CS5, plan D-07). */
public interface UnitOfWork {

    <T> T inTransaction(Supplier<T> work);
}
