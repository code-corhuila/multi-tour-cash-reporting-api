package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

/** The six numbers of the dashboard: the lengths of its six sets. */
public record DashboardCounts(
        int created, int pendingPayment, int confirmed, int confirmedWithBalance, int cancelled, int upcomingServices) {}
