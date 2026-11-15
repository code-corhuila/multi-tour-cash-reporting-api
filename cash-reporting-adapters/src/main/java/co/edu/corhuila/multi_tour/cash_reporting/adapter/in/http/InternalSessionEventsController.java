package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ApplySessionRevocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The route of the revocation of the sessions of an account (receivePlatformEvent, ADR-004 D4 and D5): only a SERVICE token,
 * no tenant in the path, because the account of a Platform Administrator has none. 201 the first time, 200 for a repeat or an
 * event older than the cutoff held. It translates: the use case checks the role and decides.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting/internal/events")
public class InternalSessionEventsController {
    private final ApplySessionRevocation apply;
    private final ObjectMapper json;

    public InternalSessionEventsController(ApplySessionRevocation apply, ObjectMapper json) {
        this.apply = apply;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<Void> receive(HttpServletRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor service = TokenFilter.actorOf(request);
        apply.authorize(service);
        String idempotencyKey = WriteRequests.key(key);
        var outcome = apply.execute(service, SessionEventRequests.revocation(json, body, idempotencyKey));
        return outcome == ApplySessionRevocation.Outcome.APPLIED
                ? ResponseEntity.status(201).build()
                : ResponseEntity.ok().build();
    }
}
