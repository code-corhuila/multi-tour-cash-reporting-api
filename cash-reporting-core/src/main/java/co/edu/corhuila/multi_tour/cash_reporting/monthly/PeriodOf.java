package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Which month a fact belongs to (RN-05, RN-18, RN-20). A cash movement belongs to the month of the business date of its
 * cash, whatever the state of that day: that date was fixed in the zone the cash was created with, so a later zone change
 * moves no month already reported. A fact that carries only an instant (a cancellation, a cost, a confirmation) is read in
 * the tenant zone in force at that instant.
 */
public final class PeriodOf {

    private PeriodOf() {}

    public static ReportedPeriod movement(RecordedMovement movement) {
        return ReportedPeriod.of(movement.date());
    }

    public static ReportedPeriod moment(Instant instant, ZoneId zone) {
        return ReportedPeriod.of(instant, zone);
    }
}
