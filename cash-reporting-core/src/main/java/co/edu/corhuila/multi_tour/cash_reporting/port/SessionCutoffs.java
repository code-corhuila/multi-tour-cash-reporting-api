package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.revocation.SessionCutoff;
import java.util.Optional;

/** Where the cutoff of each account is kept: one per account, and it only moves forward. */
public interface SessionCutoffs {

    Optional<SessionCutoff> find(String accountId);

    /**
     * Keeps the cutoff when the account has none, or when it is later than the one held; otherwise it changes nothing.
     * It answers whether the cutoff was written. The decision is one statement, so a race cannot move a cutoff back.
     */
    boolean advance(SessionCutoff cutoff);
}
