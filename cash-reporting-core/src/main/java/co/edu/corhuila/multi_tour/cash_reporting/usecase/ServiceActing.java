package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * A service acts for the tenant of the path of an internal route (ADR-004 D5). The movement is attributed to the person
 * the saga names in {@code onBehalfOf}; without one, to a fixed id derived from the service name, because who registered
 * a movement is a UUID in the database and the subject of a service token is its name.
 */
final class ServiceActing {

    private ServiceActing() {}

    static Actor actor(Actor service, String tenantId, String onBehalfOf) {
        CashGuards.requireService(service);
        String who = onBehalfOf != null && !onBehalfOf.isBlank()
                ? onBehalfOf
                : UUID.nameUUIDFromBytes(("service:" + service.userId()).getBytes(StandardCharsets.UTF_8)).toString();
        return new Actor(tenantId, who, Set.of("SERVICE"), Set.of());
    }
}
