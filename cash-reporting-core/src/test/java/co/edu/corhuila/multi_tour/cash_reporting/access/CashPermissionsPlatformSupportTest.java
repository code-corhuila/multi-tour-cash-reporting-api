package co.edu.corhuila.multi_tour.cash_reporting.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11, option A): the read only PLATFORM_SUPPORT actor of an exceptional or emergency access reads the five read operations and nothing else. */
class CashPermissionsPlatformSupportTest {

    private static final String MANAGE_CASH = "manage-cash-test";
    private static final CashPermissions POLICY = new CashPermissions(MANAGE_CASH);
    private static final Actor SUPPORT = new Actor("tenant-1", "admin-1", Set.of("PLATFORM_SUPPORT"), Set.of(MANAGE_CASH));

    @Test
    void itReadsTheFiveReadOperations() {
        for (Operation operation : new Operation[] {Operation.LIST_CASH_DAYS, Operation.GET_CURRENT_CASH_DAY, Operation.GET_CASH_DAY,
                Operation.LIST_CASH_MOVEMENTS, Operation.LIST_CASH_CLOSURES}) {
            assertThat(POLICY.allows(SUPPORT, operation)).as(operation.name()).isTrue();
        }
    }

    @Test
    void itNeverOperatesOrAdministersEvenWithThePermissionString() {
        for (Operation operation : new Operation[] {Operation.OPEN_CASH_DAY, Operation.CLOSE_CASH_DAY, Operation.REOPEN_CASH_DAY,
                Operation.REGISTER_CASH_MOVEMENT, Operation.ADJUST_CASH_BASE, Operation.REGISTER_CASH_CORRECTION}) {
            assertThat(POLICY.allows(SUPPORT, operation)).as(operation.name()).isFalse();
        }
    }

    @Test
    void itHasNoTenantNoAccess() {
        assertThat(POLICY.allows(new Actor(null, "admin-1", Set.of("PLATFORM_SUPPORT"), Set.of()), Operation.LIST_CASH_DAYS)).isFalse();
    }
}
