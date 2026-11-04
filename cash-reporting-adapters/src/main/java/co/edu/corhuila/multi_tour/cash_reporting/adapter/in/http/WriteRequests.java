package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

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

    private static Long absent(boolean required) {
        if (required) throw new InvalidFieldException(BASE_FIELD, BASE_DETAIL);
        return null;
    }
}
