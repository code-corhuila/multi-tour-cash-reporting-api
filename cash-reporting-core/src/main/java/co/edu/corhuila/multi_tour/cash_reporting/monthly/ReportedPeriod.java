package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import java.time.YearMonth;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public record ReportedPeriod(YearMonth value) implements Comparable<ReportedPeriod> {

    public static ReportedPeriod of(YearMonth month) {
        return new ReportedPeriod(month);
    }

    public static ReportedPeriod of(BusinessDate date) {
        throw new UnsupportedOperationException();
    }

    public static ReportedPeriod parse(String text) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(ReportedPeriod other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException();
    }
}
