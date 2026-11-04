package co.edu.corhuila.multi_tour.cash_reporting.access;

import java.util.Objects;
import java.util.Optional;

/**
 * The permission table of the contract as a pure policy. It fails closed: no actor, no tenant, no roles or only unknown
 * roles answer false. The string of the permission "Gestionar caja" is NO DEFINIDO (G-PERM HU-SEC-006): the composition
 * root injects it, nothing here fixes it.
 */
public final class CashPermissions {

    private final String manageCashPermission;

    public CashPermissions(String manageCashPermission) {
        Objects.requireNonNull(manageCashPermission, "manageCashPermission");
        if (manageCashPermission.isBlank()) {
            throw new IllegalArgumentException("The manage-cash permission string must not be blank");
        }
        this.manageCashPermission = manageCashPermission;
    }

    public boolean allows(Actor actor, Operation operation) {
        if (actor == null || operation == null || !actor.hasTenant()) {
            return false;
        }
        return actor.roles().stream()
                .map(Role::from)
                .flatMap(Optional::stream)
                .anyMatch(role -> roleAllows(role, actor, operation));
    }

    private boolean roleAllows(Role role, Actor actor, Operation operation) {
        return switch (role) {
            case TENANT_ADMINISTRATOR -> true;
            case OPERATIONAL_COLLABORATOR -> switch (operation.kind()) {
                case READ -> true;
                case OPERATE -> actor.holds(manageCashPermission);
                case ADMINISTER -> false;
            };
            case MANAGER, ACCOUNTANT, ANALYST -> operation.kind() == Operation.Kind.READ;
            case FINAL_CUSTOMER, PLATFORM_ADMINISTRATOR -> false;
        };
    }
}
