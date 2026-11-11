package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.Listing;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.OutboxViews.OutboxEventResponse;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.RelayOutbox;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three outbox routes of job 7 (HU-WRK-007, G-RL7-1, ADR-004 D4), the same as commercial-reservation: they read across tenants by design, so there is no
 * tenant in the path, and only a SERVICE_TOKEN reaches them (the use case checks the role; the exceptional-access policy has no area for them). Translates only.
 * PROPOSED: they are not in the approved contract yet.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting/internal/outbox")
public class OutboxRelayController {
    private static final int MAX_ERROR = 1000;

    /** The reason is free text from the consumer: it is only checked, never stored, and kept out of toString. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AttemptRequest(String error) {
        @Override
        public String toString() {
            return "AttemptRequest[error=<withheld>]";
        }
    }

    private final RelayOutbox relay;

    public OutboxRelayController(RelayOutbox relay) {
        this.relay = relay;
    }

    @GetMapping
    Listing<OutboxEventResponse> pending(HttpServletRequest request, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String status) {
        if (status != null && !"PENDING".equals(status)) {
            throw new InvalidFieldException("status", "only PENDING is read");
        }
        return Listing.of(relay.pending(TokenFilter.actorOf(request), PageRequest.of(page, limit)), OutboxEventResponse::of);
    }

    @PostMapping("/{eventId}/published")
    OutboxEventResponse published(HttpServletRequest request, @PathVariable String eventId) {
        return OutboxEventResponse.of(relay.markPublished(TokenFilter.actorOf(request), idOf(eventId)));
    }

    @PostMapping("/{eventId}/attempts")
    OutboxEventResponse attempt(HttpServletRequest request, @PathVariable String eventId, @RequestBody AttemptRequest body) {
        if (body.error() == null || body.error().isBlank() || body.error().length() > MAX_ERROR) {
            throw new InvalidFieldException("error", "error is required, 1 to " + MAX_ERROR + " characters");
        }
        return OutboxEventResponse.of(relay.recordAttempt(TokenFilter.actorOf(request), idOf(eventId)));
    }

    private static String idOf(String text) {
        try {
            return UUID.fromString(text).toString();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("The event of the path must be a UUID");
        }
    }
}
