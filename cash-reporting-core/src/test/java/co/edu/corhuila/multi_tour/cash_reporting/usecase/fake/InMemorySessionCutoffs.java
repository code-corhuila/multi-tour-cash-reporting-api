package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.SessionCutoffs;
import co.edu.corhuila.multi_tour.cash_reporting.revocation.SessionCutoff;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The cutoffs in memory: one per account, advanced only forward, like the upsert of the table. */
public final class InMemorySessionCutoffs implements SessionCutoffs {

    private final Map<String, SessionCutoff> byAccount = new HashMap<>();
    private volatile RuntimeException fault;

    /** Makes every call fail, like a database that is down; null makes it work again. */
    public void failWith(RuntimeException fault) {
        this.fault = fault;
    }

    @Override
    public Optional<SessionCutoff> find(String accountId) {
        if (fault != null) throw fault;
        return Optional.ofNullable(byAccount.get(accountId));
    }

    @Override
    public boolean advance(SessionCutoff cutoff) {
        SessionCutoff held = byAccount.get(cutoff.accountId());
        if (held != null && !cutoff.revokedFrom().isAfter(held.revokedFrom())) return false;
        byAccount.put(cutoff.accountId(), cutoff);
        return true;
    }
}
