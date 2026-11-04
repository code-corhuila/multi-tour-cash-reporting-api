package co.edu.corhuila.multi_tour.cash_reporting.access;

import java.util.Set;

/**
 * Who acts: the tenant of the token, the user, the roles and the permissions as the token carried them. Roles stay as
 * text so an unknown one is representable and the policy can refuse it. The tenant is null for a Platform Administrator.
 */
public record Actor(String tenantId, String userId, Set<String> roles, Set<String> permissions) {

    public Actor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasTenant() {
        return tenantId != null && !tenantId.isBlank();
    }

    public boolean holds(String permission) {
        return permission != null && permissions.contains(permission);
    }
}
