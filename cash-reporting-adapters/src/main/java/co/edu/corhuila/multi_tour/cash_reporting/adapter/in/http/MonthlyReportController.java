package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.Listing;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.PeriodSummary;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetMonthlyReportSummary;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListMonthlyConsolidations;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListMonthlyReports;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The monthly report, its summary and the cash consolidation (HU-CSH-003, AC-RM.1 to AC-RM.6). It translates only: the token to an
 * Actor, the page and the period to values, and the figures of the projection to the JSON of the contract. The tenant is the one
 * of the token; nothing here computes a figure.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class MonthlyReportController {

    /** The eight fields of RF-012, as the contract names them. */
    record ReportPeriod(
            String reportedPeriod,
            long periodIncomeCents,
            long operationalPaymentsCents,
            long operationalExpensesCents,
            long executedRefundsCents,
            long consolidatedCashTotalCents,
            int registeredCancellations,
            long registeredOperationalCostsCents) {}

    /** The report plus the three lines of the consolidation sub-screen. */
    record ConsolidationPeriod(
            String reportedPeriod,
            long periodIncomeCents,
            long operationalPaymentsCents,
            long operationalExpensesCents,
            long executedRefundsCents,
            long consolidatedCashTotalCents,
            int registeredCancellations,
            long registeredOperationalCostsCents,
            int individualMovementsCounted,
            long cashIncomeCents,
            long transferIncomeCents) {}

    /** The four values of the summary row; the sales are null while no source gives them (G-SALES), never a false zero. */
    record Summary(
            String reportedPeriod,
            Long confirmedSalesCents,
            long periodIncomeCents,
            long operationalCostsCents,
            int cancellations) {}

    private final ListMonthlyReports listMonthlyReports;
    private final GetMonthlyReportSummary getMonthlyReportSummary;
    private final ListMonthlyConsolidations listMonthlyConsolidations;

    public MonthlyReportController(ListMonthlyReports listMonthlyReports, GetMonthlyReportSummary getMonthlyReportSummary,
            ListMonthlyConsolidations listMonthlyConsolidations) {
        this.listMonthlyReports = listMonthlyReports;
        this.getMonthlyReportSummary = getMonthlyReportSummary;
        this.listMonthlyConsolidations = listMonthlyConsolidations;
    }

    @GetMapping("/monthly-reports")
    Listing<ReportPeriod> reports(HttpServletRequest request, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return Listing.of(listMonthlyReports.execute(TokenFilter.actorOf(request), PageRequest.of(page, limit)),
                MonthlyReportController::report);
    }

    @GetMapping("/monthly-reports/summary")
    Summary summary(HttpServletRequest request, @RequestParam(required = false) String period) {
        PeriodSummary summary = getMonthlyReportSummary.execute(TokenFilter.actorOf(request), periodOf(period));
        return new Summary(summary.period().toString(),
                summary.confirmedSales() == null ? null : summary.confirmedSales().value(),
                summary.periodIncome().value(), summary.operationalCosts().value(), summary.cancellations());
    }

    @GetMapping("/monthly-consolidations")
    Listing<ConsolidationPeriod> consolidations(HttpServletRequest request, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return Listing.of(listMonthlyConsolidations.execute(TokenFilter.actorOf(request), PageRequest.of(page, limit)),
                MonthlyReportController::consolidation);
    }

    private static ReportedPeriod periodOf(String text) {
        if (text == null) {
            return null;
        }
        try {
            return ReportedPeriod.parse(text);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidFieldException("period", "must be written YYYY-MM");
        }
    }

    private static ReportPeriod report(MonthlyPeriod month) {
        return new ReportPeriod(month.period().toString(), month.totals().income().value(),
                month.totals().operationalPayments().value(), month.totals().operationalExpenses().value(),
                month.totals().executedRefunds().value(), month.totals().total().value(), month.registeredCancellations(),
                month.registeredOperationalCosts().value());
    }

    private static ConsolidationPeriod consolidation(MonthlyPeriod month) {
        return new ConsolidationPeriod(month.period().toString(), month.totals().income().value(),
                month.totals().operationalPayments().value(), month.totals().operationalExpenses().value(),
                month.totals().executedRefunds().value(), month.totals().total().value(), month.registeredCancellations(),
                month.registeredOperationalCosts().value(), month.totals().movementCount(),
                month.totals().cashIncome().value(), month.totals().transferIncome().value());
    }
}
