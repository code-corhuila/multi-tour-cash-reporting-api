package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.port.SessionCutoffs;
import co.edu.corhuila.multi_tour.cash_reporting.revocation.SessionCutoff;
import java.util.Objects;

/**
 * The event AccountSessionsRevoked (ADR-004 D5): the account keeps a cutoff and a token issued before it is refused. Only a
 * service delivers it. The first delivery is APPLIED; a repeat of the same event, or an event older than the cutoff held,
 * is UNCHANGED, so the cutoff never moves back.
 */
public final class ApplySessionRevocation {

    /** APPLIED when the cutoff was set or moved forward; UNCHANGED for a repeat or an older event. */
    public enum Outcome { APPLIED, UNCHANGED }

    private final SessionCutoffs cutoffs;

    public ApplySessionRevocation(SessionCutoffs cutoffs) {
        this.cutoffs = Objects.requireNonNull(cutoffs, "cutoffs");
    }

    /** The permission is answered before the shape of the request is looked at. */
    public void authorize(Actor service) {
        CashGuards.requireService(service);
    }

    public Outcome execute(Actor service, SessionCutoff candidate) {
        authorize(service);
        var held = cutoffs.find(candidate.accountId());
        if (held.isPresent() && !candidate.revokedFrom().isAfter(held.get().revokedFrom())) {
            return Outcome.UNCHANGED;
        }
        return cutoffs.advance(candidate) ? Outcome.APPLIED : Outcome.UNCHANGED;
    }
}
