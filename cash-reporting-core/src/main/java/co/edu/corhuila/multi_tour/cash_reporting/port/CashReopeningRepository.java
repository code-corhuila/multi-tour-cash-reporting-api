package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import java.util.List;

/** Outbound port of the reopenings. Insert-only: a reopening is history. Every method takes the tenant first. */
public interface CashReopeningRepository {

    CashReopening insert(String tenantId, CashReopening reopening);

    /** The reopenings that point at any of the given closures. */
    List<CashReopening> listByClosures(String tenantId, List<String> closureIds);
}
