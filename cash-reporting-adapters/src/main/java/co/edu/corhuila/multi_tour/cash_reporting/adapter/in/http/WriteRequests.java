package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Shape rules of the write requests that the contract states: the key, and a body with additionalProperties false. */
final class WriteRequests {
    static final String BASE_FIELD = "openingBaseCents";
    private static final String BASE_DETAIL = "Registra una base diaria valida.";

    private WriteRequests() {}

    /** The Idempotency-Key: 8 to 128 characters, never blank. */
    static String key(String header) {
        if (header == null || header.isBlank() || header.length() < 8 || header.length() > 128) {
            throw new InvalidFieldException("Idempotency-Key", "must have 8 to 128 characters");
        }
        return header;
    }

    /**
     * The base of a body that holds only {@code openingBaseCents} (a tenantId or any other field is refused by name).
     * An absent body or an absent base is null when it is optional, and a refusal when the operation requires it.
     */
    static Long base(ObjectMapper json, byte[] body, boolean required) {
        if (body == null || body.length == 0) {
            return absent(required);
        }
        JsonNode node;
        try {
            node = json.readTree(body);
        } catch (IOException unreadable) {
            throw new InvalidFieldException("body", "must contain valid JSON");
        }
        if (node == null || node.isMissingNode()) {
            return absent(required);
        }
        if (!node.isObject()) {
            throw new InvalidFieldException("body", "must be a JSON object");
        }
        node.fieldNames().forEachRemaining(name -> {
            if (!BASE_FIELD.equals(name)) throw new InvalidFieldException(name, "is not allowed");
        });
        JsonNode base = node.get(BASE_FIELD);
        if (base == null) {
            return absent(required);
        }
        if (!base.isIntegralNumber() || !base.canConvertToLong() || base.longValue() < 0) {
            throw new InvalidFieldException(BASE_FIELD, BASE_DETAIL);
        }
        return base.longValue();
    }

    /** Plan D-06 step 2: the permission is answered before the shape of the request is looked at. */
    static void permit(CashPermissions permissions, Actor actor, Operation operation) {
        if (!permissions.allows(actor, operation)) {
            throw new CashRuleViolation.Forbidden("The user may not do " + operation);
        }
    }

    /** The movement a person types: the three types a client may send, never a REFUND (it comes from the saga). */
    record MovementBody(MovementType type, PaymentMedium medium, String concept, long amountCents) {}

    static MovementBody movement(ObjectMapper json, byte[] body) {
        ObjectNode node = object(json, body);
        if (node == null) {
            throw new InvalidFieldException("body", "is required");
        }
        List<ApiError.Detail> problems = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> {
            if (!MOVEMENT_FIELDS.contains(name)) problems.add(new ApiError.Detail(name, "is not allowed"));
        });
        MovementType type = choice(node, "movementType", MovementType.class, problems);
        if (type == MovementType.REFUND) {
            type = null;
            problems.add(new ApiError.Detail("movementType", "a refund is never registered here"));
        }
        PaymentMedium medium = choice(node, "paymentMedium", PaymentMedium.class, problems);
        JsonNode concept = node.get("concept");
        if (concept == null || !concept.isTextual() || concept.textValue().isBlank() || concept.textValue().length() > 200) {
            problems.add(new ApiError.Detail("concept", "must have 1 to 200 characters"));
        }
        JsonNode amount = node.get("amountCents");
        if (amount == null || !amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() < 1) {
            problems.add(new ApiError.Detail("amountCents", "must be an integer greater than 0"));
        }
        if (!problems.isEmpty()) throw new InvalidFieldException(problems);
        return new MovementBody(type, medium, concept.textValue(), amount.longValue());
    }

    /** The reopening reason as typed (null when absent: the use case answers REASON_REQUIRED); any other field is a 400. */
    static String reason(ObjectMapper json, byte[] body) {
        ObjectNode node = object(json, body);
        if (node == null) return null;
        List<ApiError.Detail> unknown = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> {
            if (!"reason".equals(name)) unknown.add(new ApiError.Detail(name, "is not allowed"));
        });
        if (!unknown.isEmpty()) throw new InvalidFieldException(unknown);
        JsonNode reason = node.get("reason");
        if (reason == null || reason.isNull()) return null;
        if (!reason.isTextual()) throw new InvalidFieldException("reason", "must be a string");
        return reason.textValue();
    }

    private static final Set<String> MOVEMENT_FIELDS = Set.of("movementType", "paymentMedium", "concept", "amountCents");

    private static <E extends Enum<E>> E choice(ObjectNode node, String field, Class<E> type, List<ApiError.Detail> problems) {
        JsonNode value = node.get(field);
        if (value != null && value.isTextual()) {
            try {
                return Enum.valueOf(type, value.textValue());
            } catch (IllegalArgumentException unknown) {
                // reported below with the allowed values
            }
        }
        problems.add(new ApiError.Detail(field, "must be a valid value"));
        return null;
    }

    /** The JSON object of the body, or null when there is none; anything else is a 400 on the body. */
    private static ObjectNode object(ObjectMapper json, byte[] body) {
        if (body == null || body.length == 0) return null;
        JsonNode node;
        try {
            node = json.readTree(body);
        } catch (IOException unreadable) {
            throw new InvalidFieldException("body", "must contain valid JSON");
        }
        if (node == null || node.isMissingNode()) return null;
        if (!(node instanceof ObjectNode object)) throw new InvalidFieldException("body", "must be a JSON object");
        return object;
    }

    private static Long absent(boolean required) {
        if (required) throw new InvalidFieldException(BASE_FIELD, BASE_DETAIL);
        return null;
    }
}
