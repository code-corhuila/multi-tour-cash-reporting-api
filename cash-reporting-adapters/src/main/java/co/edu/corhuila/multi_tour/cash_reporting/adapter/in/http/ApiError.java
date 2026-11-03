package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The common envelope of _shared.yaml; details only with 400. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String error, String message, List<Detail> details, String traceId) {
    public record Detail(String field, String message) {}
}
