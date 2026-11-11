package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.MovementResponse;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.Outcome;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RecordExecutedRefund;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RecordSagaIncome;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ReverseCashMovement;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes of the sagas register-payment and execute-refund, only with a SERVICE_TOKEN (ADR-004 D4 and D5). It
 * translates: the use case checks the role and the tenant is the one of the path. 201 with Location the first time,
 * 200 for a replay of the key.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting/internal/tenants/{tenantId}")
public class InternalSagaController {
    private final RecordSagaIncome recordSagaIncome;
    private final RecordExecutedRefund recordExecutedRefund;
    private final ReverseCashMovement reverseCashMovement;
    private final ObjectMapper json;

    public InternalSagaController(RecordSagaIncome recordSagaIncome, RecordExecutedRefund recordExecutedRefund,
            ReverseCashMovement reverseCashMovement, ObjectMapper json) {
        this.recordSagaIncome = recordSagaIncome;
        this.recordExecutedRefund = recordExecutedRefund;
        this.reverseCashMovement = reverseCashMovement;
        this.json = json;
    }

    @PostMapping("/cash-movements")
    ResponseEntity<MovementResponse> income(HttpServletRequest request, @PathVariable String tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor service = TokenFilter.actorOf(request);
        String tenant = InternalCashController.tenantOf(tenantId);
        String idempotencyKey = WriteRequests.key(key);
        SagaRequests.Income income = SagaRequests.income(json, body);
        return answer(request, recordSagaIncome.execute(service, tenant, income.onBehalfOf(), income.date(),
                income.medium(), income.concept(), income.amountCents(), income.reservationId(), idempotencyKey));
    }

    @PostMapping("/cash-refunds")
    ResponseEntity<MovementResponse> refund(HttpServletRequest request, @PathVariable String tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor service = TokenFilter.actorOf(request);
        String tenant = InternalCashController.tenantOf(tenantId);
        String idempotencyKey = WriteRequests.key(key);
        SagaRequests.Refund refund = SagaRequests.refund(json, body);
        return answer(request, recordExecutedRefund.register(service, tenant, refund.onBehalfOf(), refund.date(),
                refund.medium(), refund.reason(), refund.amountCents(), refund.reservationId(),
                refund.authorizationId(), idempotencyKey));
    }

    @PostMapping("/cash-movements/{movementId}/reversals")
    ResponseEntity<MovementResponse> reverse(HttpServletRequest request, @PathVariable String tenantId,
            @PathVariable String movementId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor service = TokenFilter.actorOf(request);
        String tenant = InternalCashController.tenantOf(tenantId);
        String movement = InternalCashController.tenantOf(movementId);
        String idempotencyKey = WriteRequests.key(key);
        SagaRequests.Reversal reversal = SagaRequests.reversal(json, body);
        return answer(request, reverseCashMovement.execute(service, tenant, reversal.onBehalfOf(), movement,
                reversal.reason(), idempotencyKey));
    }

    private static ResponseEntity<MovementResponse> answer(HttpServletRequest request, Outcome<RecordedMovement> done) {
        MovementResponse view = CashViews.movement(done.value());
        if (done.replayed()) {
            return ResponseEntity.ok(view);
        }
        return ResponseEntity.created(URI.create(request.getRequestURI())).body(view);
    }
}
