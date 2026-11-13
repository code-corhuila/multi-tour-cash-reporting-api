package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureStatus;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationEvent;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationStatus;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyFact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The envelope of ADR-004 D4 as job 7 delivers it (contract FactEventEnvelope): the required fields are checked, the tenant,
 * when the envelope names one, is the one of the path, and fields it does not name are tolerated up to 16 KB. The list of types
 * is closed (DP-API-12): any other type is a 400 naming {@code eventType}.
 */
final class FactEventRequests {

    static final int MAX_BYTES = 16 * 1024;
    private static final Set<String> RESERVATION = Set.of(
            "ReservationCreated", "ReservationConfirmed", "ReservationCancelled", "ReservationModified");
    private static final Set<String> DEPARTURE =
            Set.of("SalidaScheduled", "SalidaExecutionStarted", "SalidaFinalized", "SalidaCancelled");
    private static final Set<String> MONTH = Set.of("OperationalCostRecorded");
    private static final String ZONE = "TenantLocalizationChanged";

    private FactEventRequests() {}

    /**
     * What the envelope says: its type and the event of its kind. Both events are null for a type this route does not apply
     * yet and for a modification that carries no state (nothing to change for the counters).
     */
    record Parsed(String eventType, String eventId, Instant occurredAt, ReservationEvent reservation, DepartureEvent departure,
            MonthlyFact month, ZoneId zone) {}

    static Parsed parse(ObjectMapper json, byte[] body, String tenant, String key) {
        if (body != null && body.length > MAX_BYTES) {
            throw new InvalidFieldException("body", "must not be larger than 16 KB");
        }
        ObjectNode node = WriteRequests.object(json, body);
        if (node == null) throw new InvalidFieldException("body", "is required");
        List<ApiError.Detail> problems = new ArrayList<>();
        String eventId = uuid(node, "eventId", true, problems);
        String type = type(node, problems);
        JsonNode version = node.get("eventVersion");
        if (version == null || !version.isIntegralNumber() || version.longValue() < 1) {
            problems.add(new ApiError.Detail("eventVersion", "must be an integer of 1 or more"));
        }
        String aggregate = uuid(node, "aggregateId", true, problems);
        Instant occurredAt = instant(node.get("occurredAt"), "occurredAt", true, problems);
        String envelopeTenant = uuid(node, "tenantId", false, problems);
        if (envelopeTenant != null && !envelopeTenant.equals(tenant)) {
            problems.add(new ApiError.Detail("tenantId", "must be the tenant of the path"));
        }
        JsonNode payload = node.get("payload");
        if (payload == null || !payload.isObject()) {
            problems.add(new ApiError.Detail("payload", "must be an object"));
        }
        if (eventId != null && !eventId.equals(key)) {
            problems.add(new ApiError.Detail("Idempotency-Key", "must be the eventId"));
        }
        if (type != null && payload != null && payload.isObject()) {
            checkPayload(type, payload, problems);
        }
        if (!problems.isEmpty()) throw new InvalidFieldException(problems);
        if (RESERVATION.contains(type)) {
            ReservationEvent event = reservation(type, eventId, aggregate, occurredAt, payload);
            return new Parsed(type, eventId, occurredAt, event, null, monthOf(type, eventId, aggregate, occurredAt, payload), null);
        }
        if (DEPARTURE.contains(type)) {
            return new Parsed(type, eventId, occurredAt, null, departure(type, eventId, aggregate, occurredAt, payload), null, null);
        }
        if (ZONE.equals(type)) {
            return new Parsed(type, eventId, occurredAt, null, null, null, ZoneId.of(payload.get("timeZone").textValue()));
        }
        MonthlyFact cost = new MonthlyFact.OperationalCostRecorded(eventId, payload.get("costId").textValue(),
                Cents.of(payload.get("amountCents").longValue()), occurredAt);
        return new Parsed(type, eventId, occurredAt, null, null, cost, null);
    }

    /** The fields each type needs; a failure names the field, like the rest of the envelope. */
    private static void checkPayload(String type, JsonNode payload, List<ApiError.Detail> problems) {
        if (MONTH.contains(type)) {
            JsonNode cost = payload.get("costId");
            if (cost == null || !cost.isTextual() || cost.textValue().isBlank() || cost.textValue().length() > 200) {
                problems.add(new ApiError.Detail("costId", "must have 1 to 200 characters"));
            }
            JsonNode amount = payload.get("amountCents");
            if (amount == null || !amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() < 0) {
                problems.add(new ApiError.Detail("amountCents", "must be an integer of 0 or more"));
            }
        } else if (ZONE.equals(type)) {
            JsonNode zone = payload.get("timeZone");
            try {
                ZoneId.of(zone == null || !zone.isTextual() ? "" : zone.textValue());
            } catch (DateTimeException invalid) {
                problems.add(new ApiError.Detail("timeZone", "must be an IANA time zone name"));
            }
        } else if ("ReservationConfirmed".equals(type) && payload.hasNonNull("commercialValueCents")) {
            JsonNode value = payload.get("commercialValueCents");
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
                problems.add(new ApiError.Detail("commercialValueCents", "must be an integer of 0 or more"));
            }
        }
    }

    /** What the same event brings for the month: the sale of a confirmation that carries its value, a cancellation. */
    private static MonthlyFact monthOf(String type, String eventId, String aggregate, Instant at, JsonNode payload) {
        String id = text(payload, "reservationId", aggregate);
        if ("ReservationCancelled".equals(type)) {
            return new MonthlyFact.ReservationCancelled(eventId, id, at);
        }
        if ("ReservationConfirmed".equals(type) && payload.hasNonNull("commercialValueCents")) {
            return new MonthlyFact.ReservationConfirmed(eventId, id, Cents.of(payload.get("commercialValueCents").longValue()), at);
        }
        return null;
    }

    private static String type(ObjectNode node, List<ApiError.Detail> problems) {
        JsonNode value = node.get("eventType");
        if (value != null && value.isTextual() && (RESERVATION.contains(value.textValue())
                || DEPARTURE.contains(value.textValue()) || MONTH.contains(value.textValue())
                || ZONE.equals(value.textValue()))) {
            return value.textValue();
        }
        problems.add(new ApiError.Detail("eventType", "is not an event this service consumes"));
        return null;
    }

    /** The state of a reservation event: the type says it, and a modification says it in its payload or changes nothing. */
    private static ReservationEvent reservation(
            String type, String eventId, String aggregate, Instant at, JsonNode payload) {
        String id = text(payload, "reservationId", aggregate);
        String financial = text(payload, "paymentStatus", text(payload, "financialSituation", null));
        return switch (type) {
            case "ReservationCreated" -> ReservationEvent.created(eventId, id, at);
            case "ReservationConfirmed" -> ReservationEvent.state(eventId, id, ReservationStatus.CONFIRMED, financial, at);
            case "ReservationCancelled" -> ReservationEvent.state(eventId, id, ReservationStatus.CANCELLED, financial, at);
            default -> modified(eventId, id, financial, at, payload);
        };
    }

    private static ReservationEvent modified(String eventId, String id, String financial, Instant at, JsonNode payload) {
        String status = text(payload, "status", null);
        try {
            return status == null ? null : ReservationEvent.state(eventId, id, ReservationStatus.valueOf(status), financial, at);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static DepartureEvent departure(String type, String eventId, String aggregate, Instant at, JsonNode payload) {
        String id = text(payload, "departureId", aggregate);
        Instant start = startOf(payload.get("scheduledStart"));
        return switch (type) {
            case "SalidaScheduled" -> DepartureEvent.scheduled(eventId, id, start, at);
            case "SalidaExecutionStarted" -> DepartureEvent.state(eventId, id, DepartureStatus.EXECUTION_STARTED, at);
            case "SalidaFinalized" -> DepartureEvent.state(eventId, id, DepartureStatus.FINALIZED, at);
            default -> DepartureEvent.state(eventId, id, DepartureStatus.CANCELLED, at);
        };
    }

    private static Instant startOf(JsonNode value) {
        try {
            return value != null && value.isTextual() ? Instant.parse(value.textValue()) : null;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank() ? value.textValue() : fallback;
    }

    private static Instant instant(JsonNode value, String field, boolean required, List<ApiError.Detail> problems) {
        if (value == null || value.isNull()) {
            if (required) problems.add(new ApiError.Detail(field, "is required"));
            return null;
        }
        try {
            if (value.isTextual()) return Instant.parse(value.textValue());
        } catch (DateTimeParseException invalid) {
            // reported below
        }
        problems.add(new ApiError.Detail(field, "must be an ISO 8601 instant"));
        return null;
    }

    private static String uuid(ObjectNode node, String field, boolean required, List<ApiError.Detail> problems) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (required) problems.add(new ApiError.Detail(field, "is required"));
            return null;
        }
        try {
            if (value.isTextual()) return UUID.fromString(value.textValue()).toString();
        } catch (IllegalArgumentException invalid) {
            // reported below
        }
        problems.add(new ApiError.Detail(field, "must be a UUID"));
        return null;
    }
}
