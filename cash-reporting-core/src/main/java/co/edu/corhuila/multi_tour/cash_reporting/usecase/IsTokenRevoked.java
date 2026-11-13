package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.port.SessionCutoffs;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Is a valid token refused because it was issued before the cutoff of its account (ADR-004 D5, authentication.md step 4)?
 * Compared at the precision of the token (seconds): a sign-in made in the second of the revocation is a new session. A token
 * with no {@code iat} cannot be shown to be later, so it is refused when its account has a cutoff. A SERVICE token is never
 * looked up: its subject is a service name, not an account.
 */
public final class IsTokenRevoked {

    private final SessionCutoffs cutoffs;

    public IsTokenRevoked(SessionCutoffs cutoffs) {
        this.cutoffs = Objects.requireNonNull(cutoffs, "cutoffs");
    }

    public boolean execute(Actor actor, Instant issuedAt) {
        if (actor.roles().contains("SERVICE")) return false;
        String account = accountOf(actor.userId());
        if (account == null) return false;
        return cutoffs.find(account)
                .map(cutoff -> issuedAt == null || issuedAt.isBefore(cutoff.revokedFrom().truncatedTo(ChronoUnit.SECONDS)))
                .orElse(false);
    }

    private static String accountOf(String subject) {
        try {
            return UUID.fromString(subject).toString();
        } catch (RuntimeException notAnAccount) {
            return null;
        }
    }
}
