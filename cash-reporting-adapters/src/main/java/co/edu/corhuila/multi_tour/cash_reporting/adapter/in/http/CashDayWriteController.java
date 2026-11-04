package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.DayResponse;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.AdjustCashBase;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.CloseCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.OpenCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ReopenCashDay;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening, base, closing and reopening of the cash day. Translates only: token to Actor, JSON to the command, the
 * recomputed day to JSON. All answer 200 (a replay of a key too), as the contract says: H-09 of the spec. The
 * permission is checked first, then the shape (plan D-06); the use case checks it again.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class CashDayWriteController {
    private final CashPermissions permissions;
    private final OpenCashDay openCashDay;
    private final AdjustCashBase adjustCashBase;
    private final CloseCashDay closeCashDay;
    private final ReopenCashDay reopenCashDay;
    private final GetCashDay getCashDay;
    private final ObjectMapper json;

    public CashDayWriteController(CashPermissions permissions, OpenCashDay openCashDay, AdjustCashBase adjustCashBase,
            CloseCashDay closeCashDay, ReopenCashDay reopenCashDay, GetCashDay getCashDay, ObjectMapper json) {
        this.permissions = permissions;
        this.openCashDay = openCashDay;
        this.adjustCashBase = adjustCashBase;
        this.closeCashDay = closeCashDay;
        this.reopenCashDay = reopenCashDay;
        this.getCashDay = getCashDay;
        this.json = json;
    }

    @PostMapping("/cash-days/{businessDate}/open")
    DayResponse open(HttpServletRequest request, @PathVariable String businessDate,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor actor = TokenFilter.actorOf(request);
        WriteRequests.permit(permissions, actor, Operation.OPEN_CASH_DAY);
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
        WriteRequests.permit(permissions, actor, Operation.ADJUST_CASH_BASE);
        BusinessDate date = CashDayController.dateOf(businessDate);
        adjustCashBase.execute(actor, date, WriteRequests.base(json, body, true));
        return CashViews.day(getCashDay.execute(actor, date));
    }

    @PostMapping("/cash-days/{businessDate}/close")
    DayResponse close(HttpServletRequest request, @PathVariable String businessDate,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Actor actor = TokenFilter.actorOf(request);
        WriteRequests.permit(permissions, actor, Operation.CLOSE_CASH_DAY);
        BusinessDate date = CashDayController.dateOf(businessDate);
        closeCashDay.execute(actor, date, WriteRequests.key(key));
        return CashViews.day(getCashDay.execute(actor, date));
    }

    @PostMapping("/cash-days/{businessDate}/reopen")
    DayResponse reopen(HttpServletRequest request, @PathVariable String businessDate,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) byte[] body) {
        Actor actor = TokenFilter.actorOf(request);
        WriteRequests.permit(permissions, actor, Operation.REOPEN_CASH_DAY);
        BusinessDate date = CashDayController.dateOf(businessDate);
        String idempotencyKey = WriteRequests.key(key);
        reopenCashDay.execute(actor, date, WriteRequests.reason(json, body), idempotencyKey);
        return CashViews.day(getCashDay.execute(actor, date));
    }
}
