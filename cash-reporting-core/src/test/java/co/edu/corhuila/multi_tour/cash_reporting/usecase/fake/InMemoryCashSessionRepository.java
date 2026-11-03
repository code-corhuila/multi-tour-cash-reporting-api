package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory fake of the session port; it counts the writes so a test can prove that nothing was written. */
public final class InMemoryCashSessionRepository implements CashSessionRepository {

    private final Map<String, CashSession> rows = new HashMap<>();
    private int writes;

    private static String key(String tenantId, BusinessDate date) {
        return tenantId + "|" + date.value();
    }

    /** Puts a row without counting it as a write of the use case under test. */
    public void seed(CashSession session) {
        rows.put(key(session.tenantId(), session.date()), session);
    }

    public int count() {
        return rows.size();
    }

    public int writes() {
        return writes;
    }

    @Override
    public Optional<CashSession> find(String tenantId, BusinessDate date) {
        return Optional.ofNullable(rows.get(key(tenantId, date)));
    }

    @Override
    public Optional<CashSession> findForUpdate(String tenantId, BusinessDate date) {
        return find(tenantId, date);
    }

    @Override
    public Page<CashSession> list(String tenantId, PageRequest request, BusinessDate pendingAsOf) {
        List<CashSession> found = rows.values().stream()
                .filter(row -> row.tenantId().equals(tenantId))
                .filter(row -> pendingAsOf == null || row.isPending(pendingAsOf))
                .sorted(Comparator.comparing((CashSession row) -> row.date().value())
                        .reversed())
                .toList();
        return Pages.slice(found, request);
    }

    @Override
    public CashSession save(String tenantId, CashSession session) {
        writes++;
        rows.put(key(tenantId, session.date()), session);
        return session;
    }
}
