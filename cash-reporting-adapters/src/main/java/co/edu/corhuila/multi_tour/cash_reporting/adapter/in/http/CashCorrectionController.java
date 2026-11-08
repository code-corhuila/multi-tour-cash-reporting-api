package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.CorrectionResponse;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RegisterCashCorrection;
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
 * Registers the correction note of a closed cash (AC-CJ.5): Administrator only; it never changes the closure. Translates
 * only: token to Actor, JSON to the justification, result to JSON. 201 with Location the first time, 200 for a replay.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class CashCorrectionController {
    private final CashPermissions permissions;
    private final RegisterCashCorrection registerCashCorrection;
    private final IdempotencyStore keys;
    private final ObjectMapper json;

    public CashCorrectionController(CashPermissions permissions, RegisterCashCorrection registerCashCorrection,
            IdempotencyStore keys, ObjectMapper json) {
        this.permissions = permissions;
        this.registerCashCorrection = registerCashCorrection;
        this.keys = keys;
        this.json = json;
    }

    @PostMapping("/cash-days/{businessDate}/corrections")
    ResponseEntity<CorrectionResponse> register(HttpServletRequest request, @PathVariable String businessDate,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor actor = TokenFilter.actorOf(request);
        WriteRequests.permit(permissions, actor, Operation.REGISTER_CASH_CORRECTION);
        BusinessDate date = CashDayController.dateOf(businessDate);
        String idempotencyKey = WriteRequests.key(key);
        String justification = WriteRequests.justification(json, body);

        boolean replay = keys.find(actor.tenantId(), idempotencyKey).isPresent();
        CashCorrection saved = registerCashCorrection.execute(actor, date, justification, idempotencyKey);
        if (replay) {
            return ResponseEntity.ok(CashViews.correction(saved));
        }
        // The corrections are listed under their closure in the history; there is no route to read one by itself.
        return ResponseEntity.created(URI.create(request.getRequestURI())).body(CashViews.correction(saved));
    }
}
