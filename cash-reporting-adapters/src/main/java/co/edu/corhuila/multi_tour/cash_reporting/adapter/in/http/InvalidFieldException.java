package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import java.util.List;

/** A request field that breaks the contract: the response names this field in its details. */
public class InvalidFieldException extends IllegalArgumentException {
    private final List<ApiError.Detail> details;

    public InvalidFieldException(String field, String detail) {
        this(List.of(new ApiError.Detail(field, detail)));
    }

    /** Several fields at once: the response has one detail for each. */
    public InvalidFieldException(List<ApiError.Detail> details) {
        super(details.get(0).message());
        this.details = List.copyOf(details);
    }

    public List<ApiError.Detail> details() { return details; }
}
