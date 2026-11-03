package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.util.function.Supplier;

/** Fake transaction: it counts the commits and the rollbacks and says whether a unit of work is running. */
public final class InMemoryUnitOfWork implements UnitOfWork {

    private boolean active;
    private int commits;
    private int rollbacks;

    public boolean active() {
        return active;
    }

    public int commits() {
        return commits;
    }

    public int rollbacks() {
        return rollbacks;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        active = true;
        try {
            T result = work.get();
            commits++;
            return result;
        } catch (RuntimeException failure) {
            rollbacks++;
            throw failure;
        } finally {
            active = false;
        }
    }
}
