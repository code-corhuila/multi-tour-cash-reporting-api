package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ExceptionalAccessPolicy;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-SEC-007 G-SEC9-1 (ADR-004 D5): the token of an exceptional or emergency access reads only the cash area, inside its scope; anything else has no area. */
class ExceptionalAccessPolicyTest {

    private static final String BASE = "/api/v1/cash-reporting";
    private static final String ID = UUID.randomUUID().toString();

    @Test
    void everyReadRouteOfTheServiceIsTheCashArea() {
        for (String path : new String[] {"/cash-days", "/cash-days/current", "/cash-days/2026-11-03", "/cash-days/2026-11-03/movements", "/cash-closures"}) {
            assertThat(ExceptionalAccessPolicy.areaOf("GET", BASE + path)).as(path).contains("CASH");
        }
    }

    @Test
    void aWriteTheInternalRoutesAndWhatIsNotListedHaveNoArea() {
        assertThat(ExceptionalAccessPolicy.areaOf("POST", BASE + "/cash-days/2026-11-03/open")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("POST", BASE + "/cash-days/2026-11-03/movements")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("GET", BASE + "/internal/cash-days/open")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("GET", BASE + "/something-new")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("GET", BASE + "/cash-days/../internal/cash-days/open")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("GET", "/api/v1/commercial-reservation/reservations")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("GET", BASE)).isEmpty();
    }

    @Test
    void aRouteIsAllowedOnlyInsideTheScope() {
        assertThat(ExceptionalAccessPolicy.allows("GET", BASE + "/cash-days", Set.of("CASH"))).isTrue();
        assertThat(ExceptionalAccessPolicy.allows("GET", BASE + "/cash-days", Set.of("RESERVATIONS", "CUSTOMERS"))).isFalse();
        assertThat(ExceptionalAccessPolicy.allows("POST", BASE + "/cash-days/2026-11-03/close", Set.of("CASH"))).isFalse();
    }

    @Test
    void theRouteTemplateHidesTheIdentifiers() {
        assertThat(ExceptionalAccessPolicy.template(BASE + "/cash-days/" + ID + "/movements")).isEqualTo(BASE + "/cash-days/{id}/movements");
        assertThat(ExceptionalAccessPolicy.template(BASE + "/cash-days/2026-11-03")).isEqualTo(BASE + "/cash-days/2026-11-03");
    }
}
