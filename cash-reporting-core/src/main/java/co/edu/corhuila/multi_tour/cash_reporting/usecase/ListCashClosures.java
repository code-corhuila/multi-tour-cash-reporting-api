package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Lists the closing history, newest first, one entry per closing: a reopened day keeps each closure (DP-CJ8). Read only.
 * The reopenings and the corrections of the whole page are read in one call each and attached to the closure they point
 * at. Badge: AUTOMATIC_CLOSING for an automatic one, REOPENED when a reopening points at it, CLOSED otherwise.
 */
public final class ListCashClosures {

    private final CashPermissions permissions;
    private final CashClosureRepository closures;
    private final CashReopeningRepository reopenings;
    private final CashCorrectionRepository corrections;

    public ListCashClosures(
            CashPermissions permissions,
            CashClosureRepository closures,
            CashReopeningRepository reopenings,
            CashCorrectionRepository corrections) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.closures = Objects.requireNonNull(closures, "closures");
        this.reopenings = Objects.requireNonNull(reopenings, "reopenings");
        this.corrections = Objects.requireNonNull(corrections, "corrections");
    }

    public Page<ClosureEntry> execute(Actor actor, PageRequest request) {
        CashGuards.requireAllowed(permissions, actor, Operation.LIST_CASH_CLOSURES);
        Objects.requireNonNull(request, "request");
        String tenantId = actor.tenantId();

        Page<CashClosure> page = closures.list(tenantId, request);
        List<String> ids = page.items().stream().map(CashClosure::id).toList();
        Map<String, List<CashReopening>> reopeningsByClosure = reopenings.listByClosures(tenantId, ids).stream()
                .collect(Collectors.groupingBy(CashReopening::closureId));
        Map<String, List<CashCorrection>> correctionsByClosure = corrections.listByClosures(tenantId, ids).stream()
                .collect(Collectors.groupingBy(CashCorrection::closureId));
        return page.map(closure -> {
            List<CashReopening> reopened = reopeningsByClosure.getOrDefault(closure.id(), List.of());
            return new ClosureEntry(
                    closure,
                    badge(closure, reopened),
                    reopened,
                    correctionsByClosure.getOrDefault(closure.id(), List.of()));
        });
    }

    private static ClosureBadge badge(CashClosure closure, List<CashReopening> reopened) {
        if (closure.mode() == ClosingMode.AUTOMATIC) {
            return ClosureBadge.AUTOMATIC_CLOSING;
        }
        return reopened.isEmpty() ? ClosureBadge.CLOSED : ClosureBadge.REOPENED;
    }
}
