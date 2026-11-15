package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.DayResponse;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.Listing;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.OpenDayResponse;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.AutoCloseCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListOpenCashDays;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The internal routes of job 8, only with a SERVICE_TOKEN (ADR-004 D5): the sweep across tenants and the automatic
 * close of one tenant's day. Translates only: the use cases check the role SERVICE and the tenant is the one of the path.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting/internal")
public class InternalCashController {
    private final ListOpenCashDays listOpenCashDays;
    private final AutoCloseCashDay autoCloseCashDay;
    private final GetCashDay getCashDay;

    public InternalCashController(
            ListOpenCashDays listOpenCashDays, AutoCloseCashDay autoCloseCashDay, GetCashDay getCashDay) {
        this.listOpenCashDays = listOpenCashDays;
        this.autoCloseCashDay = autoCloseCashDay;
        this.getCashDay = getCashDay;
    }

    @GetMapping("/cash-days/open")
    Listing<OpenDayResponse> open(HttpServletRequest request, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        Actor service = TokenFilter.actorOf(request);
        return Listing.of(listOpenCashDays.execute(service, PageRequest.of(page, limit)), CashViews::openDay);
    }

    @PostMapping("/tenants/{tenantId}/cash-days/{businessDate}/auto-close")
    DayResponse autoClose(HttpServletRequest request, @PathVariable String tenantId, @PathVariable String businessDate) {
        Actor service = TokenFilter.actorOf(request);
        String tenant = tenantOf(tenantId);
        BusinessDate date = CashDayController.dateOf(businessDate);
        autoCloseCashDay.execute(service, tenant, date);
        return CashViews.day(getCashDay.forService(service, tenant, date));
    }

    /** The tenant of an internal route: a UUID, never a body field or a header. */
    static String tenantOf(String text) {
        try {
            return UUID.fromString(text).toString();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("The tenant of the path must be a UUID");
        }
    }
}
