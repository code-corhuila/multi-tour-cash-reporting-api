package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.util.function.Supplier;

/** Runs the work as it comes: for a use case built without a unit of work, as the tests of the core build them. */
final class Untransacted implements UnitOfWork {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
