package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.CashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import java.util.ArrayList;
import java.util.List;

/** In-memory fake of the reopening port: insert-only, it counts the writes. */
public final class InMemoryCashReopeningRepository implements CashReopeningRepository {

    private final List<CashReopening> rows = new ArrayList<>();
    private int writes;

    public int writes() {
        return writes;
    }

    public List<CashReopening> rows() {
        return List.copyOf(rows);
    }

    @Override
    public CashReopening insert(String tenantId, CashReopening reopening) {
        writes++;
        rows.add(reopening);
        return reopening;
    }

    @Override
    public List<CashReopening> listByClosures(String tenantId, List<String> closureIds) {
        return rows.stream()
                .filter(row -> row.tenantId().equals(tenantId) && closureIds.contains(row.closureId()))
                .toList();
    }
}
