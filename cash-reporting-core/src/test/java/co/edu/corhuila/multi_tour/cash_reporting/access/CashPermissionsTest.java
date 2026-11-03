package co.edu.corhuila.multi_tour.cash_reporting.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class CashPermissionsTest {

    /** A constant of the tests only: the real string is NO DEFINIDO (G-PERM HU-SEC-006) and is injected. */
    private static final String MANAGE_CASH = "test-manage-cash";

    private static final CashPermissions POLICY = new CashPermissions(MANAGE_CASH);

    private static final String ALL_ROLES =
            "TENANT_ADMINISTRATOR OPERATIONAL_COLLABORATOR MANAGER ACCOUNTANT ANALYST FINAL_CUSTOMER PLATFORM_ADMINISTRATOR";

    /**
     * The contract table copied by hand, one row per operation, one column per role, in the order of ALL_ROLES.
     * "Y" allowed, "-" forbidden. The collaborator column is read with the permission held.
     */
    private static final List<String> GRID = List.of(
            "LIST_CASH_DAYS            Y Y Y Y Y - -",
            "GET_CURRENT_CASH_DAY      Y Y Y Y Y - -",
            "GET_CASH_DAY              Y Y Y Y Y - -",
            "LIST_CASH_MOVEMENTS       Y Y Y Y Y - -",
            "LIST_CASH_CLOSURES        Y Y Y Y Y - -",
            "OPEN_CASH_DAY             Y Y - - - - -",
            "CLOSE_CASH_DAY            Y Y - - - - -",
            "REOPEN_CASH_DAY           Y Y - - - - -",
            "REGISTER_CASH_MOVEMENT    Y Y - - - - -",
            "ADJUST_CASH_BASE          Y - - - - - -",
            "REGISTER_CASH_CORRECTION  Y - - - - - -");

    private static Actor actor(String role, boolean withPermission) {
        return new Actor("tenant-1", "user-1", Set.of(role), withPermission ? Set.of(MANAGE_CASH) : Set.of());
    }

    @TestFactory
    Stream<DynamicTest> theRoleMatrixOfTheContract() {
        String[] roles = ALL_ROLES.split(" ");
        List<DynamicTest> cases = new ArrayList<>();
        for (String row : GRID) {
            String[] cells = row.trim().split("\\s+");
            Operation operation = Operation.valueOf(cells[0]);
            for (int i = 0; i < roles.length; i++) {
                boolean expected = cells[i + 1].equals("Y");
                String role = roles[i];
                cases.add(DynamicTest.dynamicTest(
                        role + " x " + operation + " = " + expected,
                        () -> assertThat(POLICY.allows(actor(role, true), operation)).isEqualTo(expected)));
            }
        }
        assertThat(cases).hasSize(77);
        assertThat(EnumSet.allOf(Operation.class)).hasSize(11);
        return cases.stream();
    }

    @Test
    void onlyTheAdministratorMayAdjustTheBase() {
        for (String role : ALL_ROLES.split(" ")) {
            boolean expected = role.equals("TENANT_ADMINISTRATOR");
            assertThat(POLICY.allows(actor(role, true), Operation.ADJUST_CASH_BASE))
                    .as(role)
                    .isEqualTo(expected);
            assertThat(POLICY.allows(actor(role, false), Operation.ADJUST_CASH_BASE))
                    .as(role + " without permission")
                    .isEqualTo(expected);
        }
    }

    @Test
    void aCollaboratorWithoutPermissionCannotOperateTheCash() {
        Actor collaborator = actor("OPERATIONAL_COLLABORATOR", false);
        for (Operation operation : List.of(
                Operation.OPEN_CASH_DAY,
                Operation.CLOSE_CASH_DAY,
                Operation.REOPEN_CASH_DAY,
                Operation.REGISTER_CASH_MOVEMENT,
                Operation.ADJUST_CASH_BASE,
                Operation.REGISTER_CASH_CORRECTION)) {
            assertThat(POLICY.allows(collaborator, operation)).as(operation.name()).isFalse();
        }
        for (Operation operation : List.of(
                Operation.LIST_CASH_DAYS,
                Operation.GET_CURRENT_CASH_DAY,
                Operation.GET_CASH_DAY,
                Operation.LIST_CASH_MOVEMENTS,
                Operation.LIST_CASH_CLOSURES)) {
            assertThat(POLICY.allows(collaborator, operation)).as(operation.name()).isTrue();
        }
        // Another permission string is not the one that opens the cash.
        Actor other = new Actor("tenant-1", "user-1", Set.of("OPERATIONAL_COLLABORATOR"), Set.of("something-else"));
        assertThat(POLICY.allows(other, Operation.OPEN_CASH_DAY)).isFalse();
        assertThat(POLICY.allows(actor("OPERATIONAL_COLLABORATOR", true), Operation.OPEN_CASH_DAY))
                .isTrue();
    }

    @Test
    void anUnknownRoleAndAMissingTenantAreForbidden() {
        for (Operation operation : Operation.values()) {
            assertThat(POLICY.allows(actor("SUPER_USER", true), operation))
                    .as("unknown " + operation)
                    .isFalse();
            assertThat(POLICY.allows(actor("tenant_administrator", true), operation))
                    .as("case " + operation)
                    .isFalse();
            assertThat(POLICY.allows(new Actor("tenant-1", "u", Set.of(), Set.of(MANAGE_CASH)), operation))
                    .as("empty roles " + operation)
                    .isFalse();
            assertThat(POLICY.allows(new Actor(null, "u", Set.of("TENANT_ADMINISTRATOR"), Set.of()), operation))
                    .as("null tenant " + operation)
                    .isFalse();
            assertThat(POLICY.allows(new Actor(" ", "u", Set.of("TENANT_ADMINISTRATOR"), Set.of()), operation))
                    .as("blank tenant " + operation)
                    .isFalse();
            assertThat(POLICY.allows(null, operation)).as("no actor " + operation).isFalse();
        }
        // An unknown role next to a valid one neither widens nor revokes what the valid one has.
        Actor mixed = new Actor("tenant-1", "u", Set.of("SUPER_USER", "MANAGER"), Set.of());
        assertThat(POLICY.allows(mixed, Operation.GET_CASH_DAY)).isTrue();
        assertThat(POLICY.allows(mixed, Operation.OPEN_CASH_DAY)).isFalse();
    }
}
