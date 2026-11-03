package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.util.List;
import java.util.function.Function;

/** One page of a list and how many items the whole list holds. */
public record Page<T>(List<T> items, int page, int limit, long total) {

    public Page {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((total + limit - 1) / limit);
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), page, limit, total);
    }
}
