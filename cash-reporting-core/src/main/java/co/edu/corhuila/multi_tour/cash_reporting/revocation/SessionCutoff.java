package co.edu.corhuila.multi_tour.cash_reporting.revocation;

import java.time.Instant;

/**
 * From when the sessions of an account no longer count (ADR-004 D5): a token issued before {@code revokedFrom} is refused.
 * The cutoff is per account. The tenant is optional: a platform account has none, and it is only kept for audit.
 * {@code eventId} is the event that set the cutoff.
 */
public record SessionCutoff(String accountId, String tenantId, Instant revokedFrom, String eventId) {

    public SessionCutoff {
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("The account is required");
        if (revokedFrom == null) throw new IllegalArgumentException("The cutoff instant is required");
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("The event id is required");
    }
}
