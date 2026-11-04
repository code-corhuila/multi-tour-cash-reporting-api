package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.DayResponse;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.AdjustCashBase;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.OpenCashDay;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening and base of the cash day. Translates only: token to Actor, JSON to the command, the recomputed day to JSON.
 * Both answer 200 (an opening that replays a key too), as the contract says: H-09 of the spec.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class CashDayWriteController {
    private final OpenCashDay openCashDay;
    private final AdjustCashBase adjustCashBase;
    private final GetCashDay getCashDay;
    private final ObjectMapper json;

    public CashDayWriteController(OpenCashDay openCashDay, AdjustCashBase adjustCashBase, GetCashDay getCashDay,
            ObjectMapper json) {
        this.openCashDay = openCashDay;
        this.adjustCashBase = adjustCashBase;
        this.getCashDay = getCashDay;
        this.json = json;
    }

    @PostMapping("/cash-days/{businessDate}/open")
    DayResponse open(HttpServletRequest request, @PathVariable String businessDate,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor actor = TokenFilter.actorOf(request);
        BusinessDate date = CashDayController.dateOf(businessDate);
        String idempotencyKey = WriteRequests.key(key);
        Long base = WriteRequests.base(json, body, false);
        openCashDay.execute(actor, date, base, idempotencyKey);
        return CashViews.day(getCashDay.execute(actor, date));
    }

    @PostMapping("/cash-days/{businessDate}/adjust-base")
    DayResponse adjustBase(HttpServletRequest request, @PathVariable String businessDate,
            @RequestBody(required = false) byte[] body) {
        Actor actor = TokenFilter.actorOf(request);
        BusinessDate date = CashDayController.dateOf(businessDate);
        adjustCashBase.execute(actor, date, WriteRequests.base(json, body, true));
        return CashViews.day(getCashDay.execute(actor, date));
    }
}
