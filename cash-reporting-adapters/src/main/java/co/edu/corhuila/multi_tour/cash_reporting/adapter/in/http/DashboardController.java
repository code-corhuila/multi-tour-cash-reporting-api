package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DashboardCounts;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DashboardIndicators;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.GetDailyDashboard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The daily dashboard (getDailyDashboard, AC-DB.1). Translates only: token to Actor, result to JSON. The route takes no
 * tenant and no date: the tenant is the claim of the token and "today" is the tenant's calendar day, decided by the service.
 */
@RestController
@RequestMapping("/api/v1/cash-reporting")
public class DashboardController {

    /** The six numbers of the screen, as the contract names them. */
    record DailyDashboardResponse(
            String reportDate,
            int createdReservations,
            int pendingPaymentReservations,
            int confirmedReservations,
            int confirmedWithBalance,
            int cancelledReservations,
            int upcomingServices) {}

    private final GetDailyDashboard getDailyDashboard;

    public DashboardController(GetDailyDashboard getDailyDashboard) {
        this.getDailyDashboard = getDailyDashboard;
    }

    @GetMapping("/daily-dashboard")
    DailyDashboardResponse dashboard(HttpServletRequest request) {
        Actor actor = TokenFilter.actorOf(request);
        DashboardIndicators day = getDailyDashboard.execute(actor);
        DashboardCounts counts = day.counts();
        return new DailyDashboardResponse(
                day.reportDate().value().toString(),
                counts.created(),
                counts.pendingPayment(),
                counts.confirmed(),
                counts.confirmedWithBalance(),
                counts.cancelled(),
                counts.upcomingServices());
    }
}
