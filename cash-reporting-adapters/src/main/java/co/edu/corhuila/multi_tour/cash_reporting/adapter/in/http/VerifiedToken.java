package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;

/**
 * A verified token: who acts, when the token is an exceptional or emergency access its claims (null otherwise), and the
 * instant it was issued ({@code iat}, null when the token has none).
 */
public record VerifiedToken(Actor actor, ExceptionalAccess exceptional, java.time.Instant issuedAt) {
}
