package co.edu.corhuila.multi_tour.cash_reporting.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TenantCalendarTest {

    /** 23:30 UTC: still the 14th in Bogota (UTC-5), already the 15th in Tokyo (UTC+9). */
    private static final Clock AT_2330_UTC = () -> Instant.parse("2026-10-14T23:30:00Z");

    private static TenantCalendar calendarIn(String zone) {
        return new ZonedTenantCalendar(AT_2330_UTC, tenantId -> ZoneId.of(zone));
    }

    @Test
    void todayIsComputedInTheTenantTimeZone() {
        TenantCalendar bogota = calendarIn("America/Bogota");
        TenantCalendar tokyo = calendarIn("Asia/Tokyo");

        assertThat(bogota.today("tenant-1")).isEqualTo(BusinessDate.of(LocalDate.of(2026, 10, 14)));
        assertThat(tokyo.today("tenant-1")).isEqualTo(BusinessDate.of(LocalDate.of(2026, 10, 15)));
        assertThat(bogota.zoneOf("tenant-1")).isEqualTo(ZoneId.of("America/Bogota"));
    }

    @Test
    void aDateAfterTodayIsRejected() {
        TenantCalendar bogota = calendarIn("America/Bogota");

        assertThatThrownBy(() -> bogota.requireNotAfterToday("tenant-1", BusinessDate.of(LocalDate.of(2026, 10, 15))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> bogota.requireNotAfterToday("tenant-1", BusinessDate.of(LocalDate.of(2026, 10, 14))))
                .doesNotThrowAnyException();
        assertThatCode(() -> bogota.requireNotAfterToday("tenant-1", BusinessDate.of(LocalDate.of(2026, 10, 1))))
                .doesNotThrowAnyException();
    }
}
