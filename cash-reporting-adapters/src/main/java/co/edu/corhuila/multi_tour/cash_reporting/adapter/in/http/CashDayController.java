package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.DayResponse;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.Listing;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.MovementResponse;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetCashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashDays;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashMovements;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The reads of the cash days and their movements. Translates only: token to Actor, parameters to the use case, result to JSON. */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class CashDayController {
    private final GetCashDay getCashDay;
    private final ListCashDays listCashDays;
    private final ListCashMovements listCashMovements;

    public CashDayController(GetCashDay getCashDay, ListCashDays listCashDays, ListCashMovements listCashMovements) {
        this.getCashDay = getCashDay;
        this.listCashDays = listCashDays;
        this.listCashMovements = listCashMovements;
    }

    /** The date of a path, or the 400 that names businessDate (the text of the user is never echoed). */
    static BusinessDate dateOf(String text) {
        try {
            return BusinessDate.of(LocalDate.parse(text));
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("The date must be a valid calendar date YYYY-MM-DD");
        }
    }

    @GetMapping("/cash-days")
    Listing<DayResponse> days(HttpServletRequest request, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Boolean pendingClosure) {
        Actor actor = TokenFilter.actorOf(request);
        return Listing.of(listCashDays.execute(actor, Boolean.TRUE.equals(pendingClosure), PageRequest.of(page, limit)),
                CashViews::day);
    }

    @GetMapping("/cash-days/current")
    DayResponse current(HttpServletRequest request) {
        return CashViews.day(getCashDay.current(TokenFilter.actorOf(request)));
    }

    @GetMapping("/cash-days/{businessDate}")
    DayResponse day(HttpServletRequest request, @PathVariable String businessDate) {
        return CashViews.day(getCashDay.execute(TokenFilter.actorOf(request), dateOf(businessDate)));
    }

    @GetMapping("/cash-days/{businessDate}/movements")
    Listing<MovementResponse> movements(HttpServletRequest request, @PathVariable String businessDate,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer limit) {
        return Listing.of(listCashMovements.execute(TokenFilter.actorOf(request), dateOf(businessDate),
                PageRequest.of(page, limit)), CashViews::movement);
    }
}
