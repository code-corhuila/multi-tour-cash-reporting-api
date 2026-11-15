package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.revocation.SessionCutoff;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ApplySessionRevocation.Outcome;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemorySessionCutoffs;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * D level, HU-SEC-005 AC4 and HU-SEC-006 AC7 (RN-CSH-API-40 to 46): the cutoff of an account is kept once, never moves back,
 * is per account whether or not the event carries a tenant, and refuses the tokens issued before it.
 */
class SessionRevocationTest {

    private static final String ACCOUNT = "7d2e9b14-6c3a-4f58-a1b0-9e8d7c6b5a40";
    private static final String OTHER_ACCOUNT = "7d2e9b14-6c3a-4f58-a1b0-9e8d7c6b5a41";
    private static final String TENANT = "00000000-0000-0000-0000-000000000123";
    private static final Instant T10 = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant T11 = Instant.parse("2026-11-12T11:00:00Z");
    private static final Actor SERVICE = new Actor(null, "multi-tour-relay", Set.of("SERVICE"), Set.of());
    private static final Actor PERSON = new Actor(TENANT, ACCOUNT, Set.of("TENANT_ADMINISTRATOR"), Set.of());

    private final InMemorySessionCutoffs cutoffs = new InMemorySessionCutoffs();
    private final ApplySessionRevocation apply = new ApplySessionRevocation(cutoffs);
    private final IsTokenRevoked revoked = new IsTokenRevoked(cutoffs);

    private static SessionCutoff cutoff(String account, String tenant, Instant from, String event) {
        return new SessionCutoff(account, tenant, from, event);
    }

    @Test
    void theFirstDeliveryAppliesAndARepeatChangesNothing() {
        assertThat(apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T10, "e1"))).isEqualTo(Outcome.APPLIED);
        assertThat(apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T10, "e1"))).isEqualTo(Outcome.UNCHANGED);
        assertThat(cutoffs.find(ACCOUNT)).contains(cutoff(ACCOUNT, TENANT, T10, "e1"));
    }

    @Test
    void aCutoffNeverMovesBackAndAnEventLaterMovesItForward() {
        apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T11, "e2"));

        assertThat(apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T10, "e1"))).isEqualTo(Outcome.UNCHANGED);
        assertThat(cutoffs.find(ACCOUNT).orElseThrow().revokedFrom()).isEqualTo(T11);
        assertThat(apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T11.plusSeconds(1), "e3"))).isEqualTo(Outcome.APPLIED);
    }

    @Test
    void theCutoffIsKeptPerAccountWithOrWithoutTenant() {
        assertThat(apply.execute(SERVICE, cutoff(ACCOUNT, null, T10, "e1"))).isEqualTo(Outcome.APPLIED);
        assertThat(apply.execute(SERVICE, cutoff(OTHER_ACCOUNT, TENANT, T10, "e2"))).isEqualTo(Outcome.APPLIED);

        assertThat(cutoffs.find(ACCOUNT).orElseThrow().tenantId()).isNull();
        assertThat(cutoffs.find(OTHER_ACCOUNT).orElseThrow().tenantId()).isEqualTo(TENANT);
    }

    @Test
    void onlyAServiceDeliversTheEvent() {
        assertThatThrownBy(() -> apply.execute(PERSON, cutoff(ACCOUNT, TENANT, T10, "e1")))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThat(cutoffs.find(ACCOUNT)).isEmpty();
    }

    @Test
    void aTokenIssuedBeforeTheCutoffIsRefusedAndALaterOneOrNoCutoffPasses() {
        apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T10, "e1"));

        assertThat(revoked.execute(PERSON, T10.minusSeconds(1))).isTrue();
        assertThat(revoked.execute(PERSON, T10)).isFalse();
        assertThat(revoked.execute(PERSON, T10.plusSeconds(60))).isFalse();
        Actor other = new Actor(TENANT, OTHER_ACCOUNT, Set.of("TENANT_ADMINISTRATOR"), Set.of());
        assertThat(revoked.execute(other, T10.minusSeconds(3600))).isFalse();
    }

    @Test
    void theCutoffIsComparedInSecondsAndATokenWithoutIatIsRefusedWhenACutoffExists() {
        apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T10.plusMillis(700), "e1"));

        assertThat(revoked.execute(PERSON, T10)).isFalse();
        assertThat(revoked.execute(PERSON, null)).isTrue();
        assertThat(revoked.execute(new Actor(TENANT, OTHER_ACCOUNT, Set.of(), Set.of()), null)).isFalse();
    }

    @Test
    void aServiceTokenAndASubjectThatIsNotAnAccountAreNeverRefused() {
        apply.execute(SERVICE, cutoff(ACCOUNT, TENANT, T10, "e1"));
        Actor serviceOfTheAccount = new Actor(null, ACCOUNT, Set.of("SERVICE"), Set.of());

        assertThat(revoked.execute(serviceOfTheAccount, T10.minusSeconds(100))).isFalse();
        assertThat(revoked.execute(SERVICE, null)).isFalse();
        assertThat(revoked.execute(new Actor(TENANT, "not-a-uuid", Set.of(), Set.of()), null)).isFalse();
    }
}
