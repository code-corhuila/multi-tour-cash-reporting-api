package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.ClosureResponse;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.CashViews.Listing;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ListCashClosures;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The closing history: one entry per closing, newest first. */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class CashClosureController {
    private final ListCashClosures listCashClosures;

    public CashClosureController(ListCashClosures listCashClosures) { this.listCashClosures = listCashClosures; }

    @GetMapping("/cash-closures")
    Listing<ClosureResponse> closures(HttpServletRequest request, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return Listing.of(listCashClosures.execute(TokenFilter.actorOf(request), PageRequest.of(page, limit)),
                CashViews::closure);
    }
}
