package co.edu.corhuila.multi_tour.cash_reporting.access;

import java.util.Optional;

/** The roles of the token that the cash policy knows. Anything else is not a role: it grants nothing. */
public enum Role {
    TENANT_ADMINISTRATOR,
    OPERATIONAL_COLLABORATOR,
    MANAGER,
    ACCOUNTANT,
    ANALYST,
    FINAL_CUSTOMER,
    PLATFORM_ADMINISTRATOR;

    /** Exact, case-sensitive match; an unknown name is empty, never an exception. */
    public static Optional<Role> from(String name) {
        for (Role role : values()) {
            if (role.name().equals(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
