package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.ADMIN;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.BOGOTA;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.COLLABORATOR_WITHOUT_PERMISSION;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.NOW;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TENANT;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.TODAY;
import static co.edu.corhuila.multi_tour.cash_reporting.usecase.CashFixture.YESTERDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemoryOutbox;
import org.junit.jupiter.api.Test;

class OpenCashDayTest {

    private final CashFixture world = new CashFixture();

    @Test
    void aSecondOpeningIsRefusedAlreadyOpen() {
        OpenCashDay open = world.openCashDay();
        open.execute(ADMIN, TODAY, 30_000_000L, "key-first-0001");

        assertThatThrownBy(() -> open.execute(ADMIN, TODAY, 30_000_000L, "key-second-001"))
                .isInstanceOf(CashRuleViolation.AlreadyOpen.class);

        assertThat(world.sessions.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).hasSize(1);
    }

    @Test
    void openingOnAClosedDayIsRefusedAlreadyClosed() {
        world.sessions.seed(CashFixture.openedOn(TODAY, 30_000_000L).close(ClosingMode.MANUAL));

        assertThatThrownBy(() -> world.openCashDay().execute(ADMIN, TODAY, null, "key-closed-0001"))
                .isInstanceOf(CashRuleViolation.AlreadyClosed.class);

        world.assertNothingWasWritten();
    }

    @Test
    void openingKeepsTheBaseWhoAndWhen() {
        CashSession day = world.openCashDay().execute(ADMIN, TODAY, 30_000_000L, "key-keeps-0001");

        assertThat(day.origin()).isEqualTo(Origin.OPERATIONAL_OPENING);
        assertThat(day.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(day.zone()).isEqualTo(BOGOTA);
        assertThat(day.opening()).isEqualTo(new CashSession.Opening(Cents.of(30_000_000L), NOW, "admin-1"));
        assertThat(world.sessions.find(TENANT, TODAY)).contains(day);
        assertThat(world.outbox.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("CashSessionOpened");
            assertThat(event.tenantId()).isEqualTo(TENANT);
            assertThat(event.aggregateId()).isEqualTo("2026-10-14");
            assertThat(event.payload()).containsEntry("businessDate", "2026-10-14");
            assertThat(event.payload()).containsEntry("openingBaseCents", 30_000_000L);
        });
        assertThat(world.keys.find(TENANT, "key-keeps-0001")).isPresent();
    }

    @Test
    void theCollaboratorOpensWithTheParametrizedBase() {
        world.parametrizedBase = Cents.of(12_500_000L);

        CashSession day = world.openCashDay().execute(COLLABORATOR, TODAY, null, "key-collab-0001");

        assertThat(day.opening().base()).isEqualTo(Cents.of(12_500_000L));
        assertThat(day.opening().by()).isEqualTo("collab-1");
    }

    @Test
    void aCollaboratorSendingABaseAtOpeningIsForbidden() {
        assertThatThrownBy(() -> world.openCashDay().execute(COLLABORATOR, TODAY, 1_000L, "key-forbid-0001"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);
        assertThatThrownBy(() -> world.openCashDay()
                        .execute(COLLABORATOR_WITHOUT_PERMISSION, TODAY, null, "key-forbid-0002"))
                .isInstanceOf(CashRuleViolation.Forbidden.class);

        world.assertNothingWasWritten();
    }

    @Test
    void openingAnotherDateThanTodayIsRefused() {
        assertThatThrownBy(() -> world.openCashDay().execute(ADMIN, YESTERDAY, 30_000_000L, "key-past-00001"))
                .isInstanceOf(CashRuleViolation.RuleViolation.class);

        world.assertNothingWasWritten();
    }

    @Test
    void aKeyOfAnotherOperationIsRefused() {
        world.keys.put(TENANT, "key-foreign-001", "CASH_MOVEMENT", "6f1c1c64-0000-4000-8000-000000000001");

        assertThatThrownBy(() -> world.openCashDay().execute(ADMIN, TODAY, 30_000_000L, "key-foreign-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");

        assertThat(world.sessions.writes()).isZero();
        assertThat(world.outbox.events()).isEmpty();
    }

    @Test
    void aRepeatedOpeningKeyReturnsTheStoredDay() {
        OpenCashDay open = world.openCashDay();
        CashSession first = open.execute(ADMIN, TODAY, 30_000_000L, "key-repeat-0001");

        CashSession again = open.execute(ADMIN, TODAY, 99_000_000L, "key-repeat-0001");

        assertThat(again).isEqualTo(first);
        assertThat(again.opening().base()).isEqualTo(Cents.of(30_000_000L));
        assertThat(world.sessions.writes()).isEqualTo(1);
        assertThat(world.keys.writes()).isEqualTo(1);
        assertThat(world.outbox.events()).hasSize(1);
    }

    @Test
    void anInvalidBaseIsRefused() {
        assertThatThrownBy(() -> world.openCashDay().execute(ADMIN, TODAY, -1L, "key-invalid-001"))
                .isInstanceOf(IllegalArgumentException.class);

        world.assertNothingWasWritten();
    }

    @Test
    void anAutomaticCashIsOpenedKeepingItsOrigin() {
        world.sessions.seed(CashSession.automatic(TENANT, TODAY, BOGOTA));

        CashSession day = world.openCashDay().execute(ADMIN, TODAY, 5_000_000L, "key-auto-000001");

        assertThat(day.origin()).isEqualTo(Origin.AUTOMATIC_CREATION);
        assertThat(day.opening().base()).isEqualTo(Cents.of(5_000_000L));
        assertThat(world.outbox.events())
                .extracting(InMemoryOutbox.Event::eventType)
                .containsExactly("CashSessionOpened");
    }
}
