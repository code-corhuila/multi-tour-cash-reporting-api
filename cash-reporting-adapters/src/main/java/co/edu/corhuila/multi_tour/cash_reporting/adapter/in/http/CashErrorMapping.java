package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** The only place that decides status and error of a response (plan D-09); the Spanish text of the mockup lives here. */
public final class CashErrorMapping {
    public enum Code {
        VALIDATION_ERROR(400, "Datos de entrada invalidos"),
        REASON_REQUIRED(400, "Registra el motivo obligatorio de la reapertura"),
        UNAUTHORIZED(401, "Token de autenticacion requerido"),
        FORBIDDEN(403, "No tienes permisos para realizar esta accion"),
        NOT_FOUND(404, "El recurso solicitado no existe"),
        ALREADY_OPEN(422, "La caja de hoy ya tiene apertura operativa"),
        ALREADY_CLOSED(422, "La caja de hoy esta cerrada: reabrela con motivo"),
        NO_OPEN_CASH(422, "Abre la caja de hoy antes de registrar efectivo"),
        PENDING_CLOSURE(422, "La caja esta pendiente de cierre y no acepta movimientos"),
        INVALID_STATUS_TRANSITION(422, "La operacion no es posible en el estado actual de la caja"),
        BUSINESS_RULE_VIOLATION(422, "La operacion no cumple una regla de la caja"),
        INTERNAL_ERROR(500, "Error interno del servidor");

        private final int status;
        private final String message;
        Code(int status, String message) { this.status = status; this.message = message; }
        public int status() { return status; }
    }

    public record Mapped(Code code, String message, List<ApiError.Detail> details) {}
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    // The core says what is wrong in plain English; the first word found names the field of the contract.
    private static final String[][] FIELDS = {{"Idempotency-Key", "Idempotency-Key"}, {"amount", "amountCents"},
        {"concept", "concept"}, {"base", "baseCents"}, {"date", "businessDate"}, {"limit", "limit"}, {"page", "page"}};

    private CashErrorMapping() {}

    public static Mapped map(Throwable failure) {
        if (failure instanceof CashRuleViolation rule) {
            return fromRule(rule);
        }
        if (failure instanceof InvalidTokenException) {
            return of(Code.UNAUTHORIZED);
        }
        if (failure instanceof InvalidFieldException field) {
            return new Mapped(Code.VALIDATION_ERROR, Code.VALIDATION_ERROR.message, field.details());
        }
        if (failure instanceof IllegalArgumentException) {
            String text = String.valueOf(failure.getMessage());
            for (String[] field : FIELDS) {
                if (text.contains(field[0])) return invalid(field[1], text);
            }
            return invalid("body", text);
        }
        if (failure instanceof MethodArgumentTypeMismatchException mismatch) {
            return invalid(mismatch.getName(), "must be a valid value");
        }
        if (failure instanceof HttpMessageNotReadableException unreadable) {
            // A field that cannot be read is named; a body that is not JSON at all is just the body.
            if (unreadable.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()
                    && mapping.getPath().get(mapping.getPath().size() - 1).getFieldName() != null) {
                return invalid(mapping.getPath().get(mapping.getPath().size() - 1).getFieldName(), "must be a valid value");
            }
            return invalid("body", "must contain valid JSON");
        }
        if (failure instanceof NoResourceFoundException || failure instanceof HttpRequestMethodNotSupportedException) {
            return of(Code.NOT_FOUND);
        }
        return of(Code.INTERNAL_ERROR);
    }

    private static Mapped fromRule(CashRuleViolation rule) {
        String text = rule.getMessage();
        return switch (rule) {
            case CashRuleViolation.AlreadyOpen r -> of(Code.ALREADY_OPEN);
            case CashRuleViolation.AlreadyClosed r -> of(Code.ALREADY_CLOSED);
            case CashRuleViolation.NoOpenCash r -> of(Code.NO_OPEN_CASH);
            case CashRuleViolation.PendingClosure r -> {
                Matcher date = DATE.matcher(text);
                yield date.find() ? new Mapped(Code.PENDING_CLOSURE, "La caja del " + date.group()
                        + " esta pendiente de cierre y no acepta movimientos", List.of()) : of(Code.PENDING_CLOSURE);
            }
            case CashRuleViolation.ReasonRequired r -> {
                boolean correction = text.contains("justification");
                yield new Mapped(Code.REASON_REQUIRED, correction ? "Registra la justificacion obligatoria de la correccion"
                        : Code.REASON_REQUIRED.message,
                        List.of(new ApiError.Detail(correction ? "justification" : "reason", "must not be blank")));
            }
            case CashRuleViolation.InvalidTransition r -> new Mapped(Code.INVALID_STATUS_TRANSITION, text, List.of());
            case CashRuleViolation.RuleViolation r -> new Mapped(Code.BUSINESS_RULE_VIOLATION, text, List.of());
            case CashRuleViolation.NotFound r -> of(Code.NOT_FOUND);
            case CashRuleViolation.Forbidden r -> of(Code.FORBIDDEN);
        };
    }

    private static Mapped of(Code code) {
        return new Mapped(code, code.message, List.of());
    }

    private static Mapped invalid(String field, String detail) {
        return new Mapped(Code.VALIDATION_ERROR, Code.VALIDATION_ERROR.message, List.of(new ApiError.Detail(field, detail)));
    }
}
