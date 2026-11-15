package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.revocation.SessionCutoff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * The envelope of AccountSessionsRevoked (contract PlatformEventEnvelope): tolerant of the fields it does not name, up to
 * 16 KB. {@code tenantId} is optional in the envelope and in the payload (a platform account has none) and is never a reason
 * to refuse, but when it comes it must be a UUID. The cutoff is per account.
 */
final class SessionEventRequests {

    static final String EVENT_TYPE = "AccountSessionsRevoked";
    private static final int MAX_BYTES = 16 * 1024;

    private SessionEventRequests() {}

    static SessionCutoff revocation(ObjectMapper json, byte[] body, String idempotencyKey) {
        JsonNode event = read(json, body);
        String eventId = uuid(event, "eventId", true);
        if (!eventId.equals(normalizedKey(idempotencyKey))) {
            throw new InvalidFieldException("Idempotency-Key", "must be the id of the event");
        }
        if (!EVENT_TYPE.equals(event.path("eventType").textValue())) {
            throw new InvalidFieldException("eventType", "must be " + EVENT_TYPE);
        }
        if (!event.path("eventVersion").isIntegralNumber() || event.path("eventVersion").intValue() < 1) {
            throw new InvalidFieldException("eventVersion", "must be an integer of 1 or more");
        }
        instant(event, "occurredAt");
        String aggregate = uuid(event, "aggregateId", true);
        JsonNode payload = event.path("payload");
        if (!payload.isObject()) throw new InvalidFieldException("payload", "must be an object");
        String account = uuid(payload, "accountId", true);
        if (!account.equals(aggregate)) throw new InvalidFieldException("aggregateId", "must be the account of the payload");
        Instant from = instant(payload, "revokedFrom");
        String tenant = uuid(event, "tenantId", false);
        String payloadTenant = uuid(payload, "tenantId", false);
        return new SessionCutoff(account, tenant != null ? tenant : payloadTenant, from, eventId);
    }

    private static String normalizedKey(String key) {
        try {
            return UUID.fromString(key).toString();
        } catch (IllegalArgumentException notAnId) {
            throw new InvalidFieldException("Idempotency-Key", "must be the id of the event");
        }
    }

    private static JsonNode read(ObjectMapper json, byte[] body) {
        if (body == null || body.length == 0) throw new InvalidFieldException("body", "is required");
        if (body.length > MAX_BYTES) throw new InvalidFieldException("body", "must not exceed 16 KB");
        try {
            JsonNode node = json.readTree(body);
            if (node == null || !node.isObject()) throw new InvalidFieldException("body", "must be a JSON object");
            return node;
        } catch (java.io.IOException notJson) {
            throw new InvalidFieldException("body", "must be valid JSON");
        }
    }

    private static String uuid(JsonNode node, String field, boolean required) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            if (required) throw new InvalidFieldException(field, "is required");
            return null;
        }
        try {
            if (!value.isTextual()) throw new IllegalArgumentException();
            return UUID.fromString(value.textValue()).toString();
        } catch (IllegalArgumentException notAnId) {
            throw new InvalidFieldException(field, "must be a UUID");
        }
    }

    private static Instant instant(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw new InvalidFieldException(field, "is required, as an RFC 3339 date and time");
        try {
            return Instant.parse(value.textValue());
        } catch (DateTimeParseException notADate) {
            throw new InvalidFieldException(field, "must be an RFC 3339 date and time in UTC");
        }
    }
}
