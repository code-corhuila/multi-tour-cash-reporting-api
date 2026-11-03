package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port of the corrections. Insert-only. Its storage is not defined yet (DP-D1-2): only an in-memory fake
 * implements it, and no production adapter exists. Every method takes the tenant first.
 */
public interface CashCorrectionRepository {

    CashCorrection insert(String tenantId, CashCorrection correction);

    Optional<CashCorrection> find(String tenantId, String correctionId);

    /** The corrections that point at any of the given closures. */
    List<CashCorrection> listByClosures(String tenantId, List<String> closureIds);
}
