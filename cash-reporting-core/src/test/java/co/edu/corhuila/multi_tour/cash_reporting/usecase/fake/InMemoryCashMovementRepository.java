package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory fake of the movement port; it counts the writes so a test can prove that nothing was written. */
public final class InMemoryCashMovementRepository implements CashMovementRepository {

    private record Row(String tenantId, RecordedMovement movement) {}

    private final List<Row> rows = new ArrayList<>();
    private final List<Map.Entry<String, CashMovement>> seeded = new ArrayList<>();
    private int writes;

    public int writes() {
        return writes;
    }

    /** Puts a plain movement of a day without counting it as a write of the use case under test. */
    public void seed(String tenantId, BusinessDate date, CashMovement movement) {
        seeded.add(Map.entry(tenantId + "|" + date.value(), movement));
    }

    @Override
    public List<CashMovement> findByDate(String tenantId, BusinessDate date) {
        String day = tenantId + "|" + date.value();
        List<CashMovement> found = new ArrayList<>();
        seeded.stream().filter(entry -> entry.getKey().equals(day)).forEach(entry -> found.add(entry.getValue()));
        rows.stream()
                .filter(row -> row.tenantId().equals(tenantId)
                        && row.movement().date().equals(date))
                .forEach(row -> found.add(row.movement().movement()));
        return found;
    }

    @Override
    public Optional<RecordedMovement> findById(String tenantId, String id) {
        return rows.stream()
                .filter(row -> row.tenantId().equals(tenantId) && row.movement().id().equals(id))
                .map(Row::movement)
                .findFirst();
    }

    @Override
    public RecordedMovement insert(String tenantId, RecordedMovement movement) {
        writes++;
        rows.add(new Row(tenantId, movement));
        return movement;
    }
}
