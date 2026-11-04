package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

/** A request field that breaks the contract: the response names this field in its details. */
public class InvalidFieldException extends IllegalArgumentException {
    private final String field;

    public InvalidFieldException(String field, String detail) {
        super(detail);
        this.field = field;
    }

    public String field() { return field; }
}
