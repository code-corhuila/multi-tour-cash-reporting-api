package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.regex.Pattern;

/** A calendar month of a tenant: the period a monthly report row stands for ({@code YYYY-MM}). */
public record ReportedPeriod(YearMonth value) implements Comparable<ReportedPeriod> {

    private static final Pattern FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    public ReportedPeriod {
        Objects.requireNonNull(value, "value");
    }

    public static ReportedPeriod of(YearMonth month) {
        return new ReportedPeriod(month);
    }

    public static ReportedPeriod of(BusinessDate date) {
        return new ReportedPeriod(YearMonth.from(date.value()));
    }

    public static ReportedPeriod of(Instant instant, ZoneId zone) {
        return of(BusinessDate.of(instant, zone));
    }

    /** Reads {@code YYYY-MM} exactly; anything else is refused (the adapter turns it into a validation error). */
    public static ReportedPeriod parse(String text) {
        if (text == null || !FORMAT.matcher(text).matches()) {
            throw new IllegalArgumentException("A period must be written YYYY-MM");
        }
        return new ReportedPeriod(YearMonth.parse(text));
    }

    @Override
    public int compareTo(ReportedPeriod other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
