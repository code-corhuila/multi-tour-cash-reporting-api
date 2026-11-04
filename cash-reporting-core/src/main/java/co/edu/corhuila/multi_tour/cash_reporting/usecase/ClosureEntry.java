package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import java.util.List;

/** One closing of the history with the reopenings and the corrections that point at it. */
public record ClosureEntry(
        CashClosure closure, ClosureBadge badge, List<CashReopening> reopenings, List<CashCorrection> corrections) {

    public ClosureEntry {
        reopenings = List.copyOf(reopenings);
        corrections = List.copyOf(corrections);
    }
}
