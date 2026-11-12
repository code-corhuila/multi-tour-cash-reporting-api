package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ConsumeOutcome;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ReceiveFactEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The receiving route of job 7 for the events of the dashboard (receiveFactEvent, AC-DB.9, AC-DB.17), only with a SERVICE_TOKEN.
 * It translates: the tenant is the one of the path, the key is the event id, 201 the first time and 200 for a repeat.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting/internal/tenants/{tenantId}")
public class InternalEventsController {
    private final ReceiveFactEvent receiveFactEvent;
    private final ObjectMapper json;

    public InternalEventsController(ReceiveFactEvent receiveFactEvent, ObjectMapper json) {
        this.receiveFactEvent = receiveFactEvent;
        this.json = json;
    }

    @PostMapping("/events")
    ResponseEntity<Void> receive(HttpServletRequest request, @PathVariable String tenantId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor service = TokenFilter.actorOf(request);
        receiveFactEvent.authorize(service);
        String tenant = InternalCashController.tenantOf(tenantId);
        String idempotencyKey = WriteRequests.key(key);
        FactEventRequests.Parsed event = FactEventRequests.parse(json, body, tenant, idempotencyKey);
        ConsumeOutcome outcome;
        if (event.reservation() != null) {
            outcome = receiveFactEvent.reservation(service, tenant, event.eventType(), event.reservation());
        } else if (event.departure() != null) {
            outcome = receiveFactEvent.departure(service, tenant, event.eventType(), event.departure());
        } else if (event.notYetApplied()) {
            throw new IllegalStateException("The event type is not applied by this route yet");
        } else {
            return ResponseEntity.ok().build();
        }
        return outcome == ConsumeOutcome.APPLIED ? ResponseEntity.status(201).build() : ResponseEntity.ok().build();
    }
}
