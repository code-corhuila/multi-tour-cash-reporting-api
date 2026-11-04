package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.ParametrizedBase;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** I level: the base a Collaborator opens with, read from PostgreSQL (DP-CJ1), and the ids the service creates. */
@RequiresTestDatabase
class OpenAndBasePersistenceTest extends PersistenceTestBase {

    @Autowired
    ParametrizedBase parametrizedBase;

    @Autowired
    IdGenerator ids;

    private final String tenant = newId();
    private final String user = newId();

    @Test
    void theParametrizedBaseIsTheBaseOfTheLatestOperationalOpeningAdjustmentsIncluded() {
        sessions.save(tenant, opened(tenant, day(3), user));
        sessions.save(tenant, opened(tenant, day(4), user).adjustBase(Cents.of(70_000)));
        sessions.save(tenant, CashSession.automatic(tenant, day(5), ZONE));

        assertThat(parametrizedBase.forTenant(tenant)).isEqualTo(Cents.of(70_000));
    }

    @Test
    void aTenantThatNeverOpenedGetsZeroAndNeverTheBaseOfAnotherTenant() {
        String other = newId();
        sessions.save(other, opened(other, day(3), user));
        sessions.save(tenant, CashSession.automatic(tenant, day(3), ZONE));

        assertThat(parametrizedBase.forTenant(tenant)).isEqualTo(Cents.of(0));
        assertThat(parametrizedBase.forTenant(newId())).isEqualTo(Cents.of(0));
    }

    @Test
    void everyGeneratedIdIsADifferentUuid() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 50; i++) {
            assertThat(seen.add(UUID.fromString(ids.next()).toString())).isTrue();
        }
    }
}
