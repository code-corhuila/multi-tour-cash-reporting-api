package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

    /** Puts a stored movement without counting it as a write of the use case under test. */
    public void seedRecorded(String tenantId, RecordedMovement movement) {
        rows.add(new Row(tenantId, movement));
    }

    @Override
    public Page<RecordedMovement> list(String tenantId, BusinessDate date, PageRequest request) {
        List<RecordedMovement> found = new ArrayList<>(rows.stream()
                .filter(row -> row.tenantId().equals(tenantId)
                        && row.movement().date().equals(date))
                .map(Row::movement)
                .toList());
        Collections.reverse(found);
        found.sort(Comparator.comparing(RecordedMovement::registeredAt).reversed());
        return Pages.slice(found, request);
    }

    @Override
    public Map<BusinessDate, List<CashMovement>> findByDates(String tenantId, List<BusinessDate> dates) {
        Map<BusinessDate, List<CashMovement>> byDate = new HashMap<>();
        dates.forEach(date -> byDate.put(date, findByDate(tenantId, date)));
        return byDate;
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
    public Optional<RecordedMovement> findReversalOf(String tenantId, String movementId) {
        return rows.stream()
                .filter(row -> row.tenantId().equals(tenantId) && movementId.equals(row.movement().reversesMovementId()))
                .map(Row::movement)
                .findFirst();
    }

    @Override
    public Optional<RecordedMovement> findIncomeOfPayment(String tenantId, String paymentId) {
        return rows.stream()
                .filter(row -> row.tenantId().equals(tenantId) && paymentId.equals(row.movement().paymentReference()))
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
