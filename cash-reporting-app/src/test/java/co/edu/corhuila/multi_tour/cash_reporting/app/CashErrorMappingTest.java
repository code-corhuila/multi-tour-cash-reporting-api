package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ApiError;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashErrorMapping;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashErrorMapping.Mapped;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C level, without a server: the one class that decides status and {@code error} for the cash rules (plan D-09). */
class CashErrorMappingTest {

    private static void expect(RuntimeException failure, int status, String error, String message) {
        Mapped mapped = CashErrorMapping.map(failure);
        assertThat(mapped.code().status()).as(error).isEqualTo(status);
        assertThat(mapped.code().name()).isEqualTo(error);
        assertThat(mapped.message()).isEqualTo(message);
    }

    @Test
    void theFiveDomainCodesComeFromTheTableOfTheContract() {
        expect(new CashRuleViolation.AlreadyOpen("The cash of 2026-11-03 is already open"), 422, "ALREADY_OPEN",
                "La caja de hoy ya tiene apertura operativa");
        expect(new CashRuleViolation.AlreadyClosed("The cash of 2026-11-03 is closed"), 422, "ALREADY_CLOSED",
                "La caja de hoy esta cerrada: reabrela con motivo");
        expect(new CashRuleViolation.NoOpenCash("The cash of 2026-11-03 has no operational opening"), 422,
                "NO_OPEN_CASH", "Abre la caja de hoy antes de registrar efectivo");
        expect(new CashRuleViolation.PendingClosure("The cash of 2026-08-31 is pending closure"), 422,
                "PENDING_CLOSURE", "La caja del 2026-08-31 esta pendiente de cierre y no acepta movimientos");
        expect(new CashRuleViolation.ReasonRequired("The reopening reason is required (1 to 500 characters)"), 400,
                "REASON_REQUIRED", "Registra el motivo obligatorio de la reapertura");
    }

    @Test
    void theReasonRequiredNamesItsField() {
        assertThat(CashErrorMapping.map(new CashRuleViolation.ReasonRequired("The reopening reason is required")).details())
                .containsExactly(new ApiError.Detail("reason", "must not be blank"));
        assertThat(CashErrorMapping.map(new CashRuleViolation.ReasonRequired("The justification is required")).details())
                .containsExactly(new ApiError.Detail("justification", "must not be blank"));
    }

    @Test
    void theOtherRulesUseTheCommonCodes() {
        expect(new CashRuleViolation.InvalidTransition("The cash of 2026-11-03 is not closed"), 422,
                "INVALID_STATUS_TRANSITION", "The cash of 2026-11-03 is not closed");
        expect(new CashRuleViolation.RuleViolation("Only today can be opened: 2026-11-02"), 422,
                "BUSINESS_RULE_VIOLATION", "Only today can be opened: 2026-11-02");
        expect(new CashRuleViolation.NotFound("There is no cash on 2026-11-03"), 404, "NOT_FOUND",
                "El recurso solicitado no existe");
        expect(new CashRuleViolation.Forbidden("The user may not do OPEN_CASH_DAY"), 403, "FORBIDDEN",
                "No tienes permisos para realizar esta accion");
    }

    @Test
    void aShapeErrorIsOneValidationDetailPerField() {
        Mapped key = CashErrorMapping.map(new IllegalArgumentException("The Idempotency-Key must have 8 to 128 characters"));
        assertThat(key.code().name()).isEqualTo("VALIDATION_ERROR");
        assertThat(key.code().status()).isEqualTo(400);
        assertThat(key.details()).hasSize(1);
        assertThat(key.details().get(0).field()).isEqualTo("Idempotency-Key");
        assertThat(CashErrorMapping.map(new IllegalArgumentException("An amount must be greater than 0")).details()
                .get(0).field()).isEqualTo("amountCents");
        assertThat(CashErrorMapping.map(new IllegalArgumentException("The date is after today: 2027-01-01")).details()
                .get(0).field()).isEqualTo("businessDate");
    }

    @Test
    void anythingElseIsANeutralInternalError() {
        Mapped mapped = CashErrorMapping.map(new IllegalStateException("jdbc:postgresql://db.internal password=secret"));
        assertThat(mapped.code().name()).isEqualTo("INTERNAL_ERROR");
        assertThat(mapped.code().status()).isEqualTo(500);
        assertThat(mapped.message()).isEqualTo("Error interno del servidor").doesNotContain("jdbc", "secret");
        assertThat(mapped.details()).isEqualTo(List.of());
    }
}
