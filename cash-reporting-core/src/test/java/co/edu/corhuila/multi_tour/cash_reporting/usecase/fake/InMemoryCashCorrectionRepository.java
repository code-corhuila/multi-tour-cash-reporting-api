package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.CashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory fake of the correction port: the only store there is for now (DD-4). It counts the writes. */
public final class InMemoryCashCorrectionRepository implements CashCorrectionRepository {

    private final List<CashCorrection> rows = new ArrayList<>();
    private int writes;

    public int writes() {
        return writes;
    }

    public List<CashCorrection> rows() {
        return List.copyOf(rows);
    }

    @Override
    public CashCorrection insert(String tenantId, CashCorrection correction) {
        writes++;
        rows.add(correction);
        return correction;
    }

    @Override
    public Optional<CashCorrection> find(String tenantId, String correctionId) {
        return rows.stream()
                .filter(row -> row.tenantId().equals(tenantId) && row.id().equals(correctionId))
                .findFirst();
    }

    @Override
    public List<CashCorrection> listByClosures(String tenantId, List<String> closureIds) {
        return rows.stream()
                .filter(row -> row.tenantId().equals(tenantId) && closureIds.contains(row.closureId()))
                .toList();
    }
}
