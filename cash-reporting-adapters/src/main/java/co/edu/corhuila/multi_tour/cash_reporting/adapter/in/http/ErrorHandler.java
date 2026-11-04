package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashErrorMapping.Code;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashErrorMapping.Mapped;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Builds the envelope with the correlation id as traceId: the advice for the controllers, write() for the filters. */
@RestControllerAdvice
public class ErrorHandler {
    static final String OUTCOME_ATTRIBUTE = "cashOutcome";
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    private final ObjectMapper json;

    public ErrorHandler(ObjectMapper json) { this.json = json; }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handle(Exception failure, HttpServletRequest request) {
        Mapped mapped = CashErrorMapping.map(failure);
        // The type only: the message of an unexpected failure may carry data that must not reach a log line.
        if (mapped.code() == Code.INTERNAL_ERROR) LOG.error("Unexpected failure type={}", failure.getClass().getName());
        return ResponseEntity.status(mapped.code().status()).body(body(request, mapped));
    }

    void write(HttpServletRequest request, HttpServletResponse response, Mapped mapped) throws IOException {
        response.setStatus(mapped.code().status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body(request, mapped));
    }

    private static ApiError body(HttpServletRequest request, Mapped mapped) {
        request.setAttribute(OUTCOME_ATTRIBUTE, mapped.code().name());
        return new ApiError(mapped.code().name(), mapped.message(), mapped.details(),
                (String) request.getAttribute(CorrelationFilter.TRACE_ATTRIBUTE));
    }
}
