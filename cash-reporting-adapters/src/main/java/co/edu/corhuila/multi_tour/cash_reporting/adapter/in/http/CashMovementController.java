package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.MovementResponse;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.WriteRequests.MovementBody;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RegisterCashMovement;
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
 * Registers a movement typed by a person: always MANUAL. A refund has no route here (it comes from the saga with the
 * source SAGA, through the use case, never through HTTP: plan D-15) and the body refuses it as it refuses any other
 * field the contract does not name. 201 with Location the first time, 200 for a replay of the key.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class CashMovementController {
    private final CashPermissions permissions;
    private final RegisterCashMovement registerCashMovement;
    private final ObjectMapper json;

    public CashMovementController(CashPermissions permissions, RegisterCashMovement registerCashMovement,
            ObjectMapper json) {
        this.permissions = permissions;
        this.registerCashMovement = registerCashMovement;
        this.json = json;
    }

    @PostMapping("/cash-days/{businessDate}/movements")
    ResponseEntity<MovementResponse> register(HttpServletRequest request, @PathVariable String businessDate,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor actor = TokenFilter.actorOf(request);
        WriteRequests.permit(permissions, actor, Operation.REGISTER_CASH_MOVEMENT);
        BusinessDate date = CashDayController.dateOf(businessDate);
        String idempotencyKey = WriteRequests.key(key);
        MovementBody movement = WriteRequests.movement(json, body);

        var saved = registerCashMovement.register(actor, MovementSource.MANUAL, date, movement.type(),
                movement.medium(), movement.concept(), movement.amountCents(), idempotencyKey);
        if (saved.replayed()) {
            return ResponseEntity.ok(CashViews.movement(saved.value()));
        }
        // The movements of the day are listed at this same path: there is no route to read one movement by itself.
        return ResponseEntity.created(URI.create(request.getRequestURI())).body(CashViews.movement(saved.value()));
    }
}
