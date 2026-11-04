package co.edu.corhuila.multi_tour.cash_reporting.port;

/** Which page of a list is asked for: page from 1, limit from 1 to 100 (default 20). */
public record PageRequest(int page, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("The page starts at 1");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("The limit must be between 1 and " + MAX_LIMIT);
        }
    }

    /** Applies the defaults to the parameters a client left out. */
    public static PageRequest of(Integer page, Integer limit) {
        return new PageRequest(page == null ? 1 : page, limit == null ? DEFAULT_LIMIT : limit);
    }

    public int offset() {
        return (page - 1) * limit;
    }
}
