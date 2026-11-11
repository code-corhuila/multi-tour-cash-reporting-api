package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import java.util.UUID;

/** One use of the token of an exceptional access (HU-SEC-007 G-SEC9-1): identifiers, the route template and the area, never data of the request or of the answer. */
public record ExceptionalAccessUse(UUID authorizationId, String platformAdministratorId, String tenantId, String access, String area, String method, String routeTemplate, String correlationId) {}
