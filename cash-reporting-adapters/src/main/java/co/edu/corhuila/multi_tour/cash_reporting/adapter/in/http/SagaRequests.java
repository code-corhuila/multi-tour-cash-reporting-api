package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The strict bodies of the saga routes (contract SagaCash*Request): an unknown field, a missing one or a bad type is a 400. */
final class SagaRequests {

    private SagaRequests() {}

    record Income(BusinessDate date, PaymentMedium medium, long amountCents, String reservationId, String concept,
            String onBehalfOf) {}

    record Refund(BusinessDate date, PaymentMedium medium, long amountCents, String reservationId,
            String authorizationId, String reason, String onBehalfOf) {}

    record Reversal(String reason, String onBehalfOf) {}

    private static final Set<String> REVERSAL_FIELDS = Set.of("reason", "onBehalfOf");

    static Reversal reversal(ObjectMapper json, byte[] body) {
        List<ApiError.Detail> problems = new ArrayList<>();
        ObjectNode node = node(json, body, REVERSAL_FIELDS, problems);
        String reason = text(node, "reason", problems);
        String behalf = uuid(node, "onBehalfOf", false, problems);
        if (!problems.isEmpty()) throw new InvalidFieldException(problems);
        return new Reversal(reason, behalf);
    }

    private static final Set<String> INCOME_FIELDS =
            Set.of("businessDate", "paymentMedium", "amountCents", "reservationId", "concept", "onBehalfOf");
    private static final Set<String> REFUND_FIELDS = Set.of("businessDate", "outflowMedium", "amountCents",
            "reservationId", "refundAuthorizationId", "reason", "onBehalfOf");

    static Income income(ObjectMapper json, byte[] body) {
        List<ApiError.Detail> problems = new ArrayList<>();
        ObjectNode node = node(json, body, INCOME_FIELDS, problems);
        BusinessDate date = date(node, problems);
        PaymentMedium medium = WriteRequests.choice(node, "paymentMedium", PaymentMedium.class, problems);
        long amount = amount(node, problems);
        String reservation = uuid(node, "reservationId", true, problems);
        String concept = text(node, "concept", problems);
        String behalf = uuid(node, "onBehalfOf", false, problems);
        if (!problems.isEmpty()) throw new InvalidFieldException(problems);
        return new Income(date, medium, amount, reservation, concept, behalf);
    }

    static Refund refund(ObjectMapper json, byte[] body) {
        List<ApiError.Detail> problems = new ArrayList<>();
        ObjectNode node = node(json, body, REFUND_FIELDS, problems);
        BusinessDate date = date(node, problems);
        PaymentMedium medium = WriteRequests.choice(node, "outflowMedium", PaymentMedium.class, problems);
        long amount = amount(node, problems);
        String reservation = uuid(node, "reservationId", true, problems);
        String authorization = uuid(node, "refundAuthorizationId", true, problems);
        String reason = text(node, "reason", problems);
        String behalf = uuid(node, "onBehalfOf", false, problems);
        if (!problems.isEmpty()) throw new InvalidFieldException(problems);
        return new Refund(date, medium, amount, reservation, authorization, reason, behalf);
    }

    private static ObjectNode node(ObjectMapper json, byte[] body, Set<String> allowed, List<ApiError.Detail> problems) {
        ObjectNode node = WriteRequests.object(json, body);
        if (node == null) throw new InvalidFieldException("body", "is required");
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) problems.add(new ApiError.Detail(name, "is not allowed"));
        });
        return node;
    }

    private static BusinessDate date(ObjectNode node, List<ApiError.Detail> problems) {
        JsonNode value = node.get("businessDate");
        try {
            if (value != null && value.isTextual()) return BusinessDate.of(LocalDate.parse(value.textValue()));
        } catch (DateTimeParseException invalid) {
            // reported below
        }
        problems.add(new ApiError.Detail("businessDate", "must be a calendar date YYYY-MM-DD"));
        return null;
    }

    private static long amount(ObjectNode node, List<ApiError.Detail> problems) {
        JsonNode amount = node.get("amountCents");
        if (amount == null || !amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() < 1) {
            problems.add(new ApiError.Detail("amountCents", "must be an integer greater than 0"));
            return 0;
        }
        return amount.longValue();
    }

    private static String text(ObjectNode node, String field, List<ApiError.Detail> problems) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 200) {
            problems.add(new ApiError.Detail(field, "must have 1 to 200 characters"));
            return null;
        }
        return value.textValue();
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
