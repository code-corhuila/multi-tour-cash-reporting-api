package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.time.Instant;
import java.time.ZoneId;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public final class PeriodOf {

    private PeriodOf() {}

    public static ReportedPeriod movement(RecordedMovement movement) {
        throw new UnsupportedOperationException();
    }

    public static ReportedPeriod moment(Instant instant, ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
