package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;

/** A verified token: who acts and, when the token is an exceptional or emergency access, its claims (null otherwise). */
public record VerifiedToken(Actor actor, ExceptionalAccess exceptional) {
}
