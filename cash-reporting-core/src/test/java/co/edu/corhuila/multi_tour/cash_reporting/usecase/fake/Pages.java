package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.List;

/** Cuts an already sorted list into the page a request asks for, as the database would. */
final class Pages {

    private Pages() {}

    static <T> Page<T> slice(List<T> sorted, PageRequest request) {
        int from = Math.min(request.offset(), sorted.size());
        int to = Math.min(from + request.limit(), sorted.size());
        return new Page<>(sorted.subList(from, to), request.page(), request.limit(), sorted.size());
    }
}
