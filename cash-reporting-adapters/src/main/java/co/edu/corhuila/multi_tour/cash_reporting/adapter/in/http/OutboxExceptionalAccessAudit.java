package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes each use of the token of an exceptional access to the outbox of this service as PlatformExceptionalAccessUsed (HU-SEC-007 G-SEC9-1; Project Lead, 2026-11-11): the authorization is the
 * aggregate and the payload is closed to eight keys. The relay carries it to identity-audit. If the outbox cannot be written the failure is logged (identifiers only) and the read is refused with 500 (fail closed, Project Lead, 2026-11-11):
 * a privileged access that cannot be audited is not served.
 */
@Component
public class OutboxExceptionalAccessAudit implements ExceptionalAccessAudit {
    public static final String EVENT_TYPE = "PlatformExceptionalAccessUsed";
    private static final Logger LOG = LoggerFactory.getLogger(OutboxExceptionalAccessAudit.class);

    private final OutboxWriter outbox;
    private final Clock clock;

    public OutboxExceptionalAccessAudit(OutboxWriter outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void used(ExceptionalAccessUse use) {
        try {
            // One instant for both: usedAt in the payload (spec 012 RN-SEC-012-07) and occurred_at in the envelope; microseconds, which is what the column keeps.
            Instant usedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("authorizationId", use.authorizationId().toString());
            payload.put("platformAdministratorId", use.platformAdministratorId());
            payload.put("tenantId", use.tenantId());
            payload.put("method", use.method());
            payload.put("route", use.routeTemplate());
            payload.put("area", use.area());
            payload.put("usedAt", usedAt.toString());
            payload.put("correlationId", use.correlationId());
            outbox.append(use.tenantId(), EVENT_TYPE, use.authorizationId().toString(), payload, usedAt);
        } catch (RuntimeException failure) {
            LOG.error("exceptional access use not audited authorizationId={} access={} area={} method={} route={} cause={}", use.authorizationId(), use.access(), use.area(), use.method(),
                    use.routeTemplate(), failure.getClass().getSimpleName());
            throw new ExceptionalAccessAuditException();
        }
    }
}
