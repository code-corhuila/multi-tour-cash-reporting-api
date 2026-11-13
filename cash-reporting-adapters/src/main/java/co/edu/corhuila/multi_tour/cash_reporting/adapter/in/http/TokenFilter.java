package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.IsTokenRevoked;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Every route but GET /health needs a valid RS256 token; a token without tenant is refused with 403 (plan D-13). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TokenFilter extends OncePerRequestFilter {
    public static final String ACTOR_ATTRIBUTE = "cashActor";

    private final Rs256TokenVerifier verifier;
    private final ErrorHandler errors;
    private final ExceptionalAccessAudit audit;
    private final IsTokenRevoked revoked;

    public TokenFilter(Rs256TokenVerifier verifier, ErrorHandler errors, ExceptionalAccessAudit audit, IsTokenRevoked revoked) {
        this.revoked = revoked;
        this.verifier = verifier;
        this.errors = errors;
        this.audit = audit;
    }

    public static Actor actorOf(HttpServletRequest request) {
        return (Actor) request.getAttribute(ACTOR_ATTRIBUTE);
    }

    private static boolean isInternal(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length())
                .startsWith("/api/v1/cash-reporting/internal/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "/health".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Actor actor;
        ExceptionalAccess exceptional;
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new InvalidTokenException();
            VerifiedToken verified = verifier.verifyToken(authorization.substring(7));
            // HU-SEC-005 AC4, HU-SEC-006 AC7 (ADR-004 D5): a token issued before the cutoff of its account is a 401. A fault of the
            // database is not caught here: it is a 500, never a token let through.
            if (revoked.execute(verified.actor(), verified.issuedAt())) throw new InvalidTokenException();
            actor = verified.actor();
            exceptional = verified.exceptional();
        } catch (InvalidTokenException failure) {
            errors.write(request, response, CashErrorMapping.map(failure));
            return;
        }
        if (exceptional != null) {
            // HU-SEC-007 G-SEC9-1 (ADR-004 D5): only a read inside the scope; any write, internal route or route not listed is refused, and each use is audited with the authorizationId.
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!ExceptionalAccessPolicy.allows(request.getMethod(), path, exceptional.scope())) {
                errors.write(request, response, CashErrorMapping.map(new CashRuleViolation.Forbidden("The exceptional access is read only and limited to its scope")));
                return;
            }
        }
        if (isInternal(request)) {
            // A SERVICE_TOKEN has no tenant: the tenant of an internal route is the one of its path (ADR-004 D5).
            // An internal route accepts only a service (ADR-004 D5): any other token is refused here, before the body is read.
            if (!actor.roles().contains("SERVICE")) {
                errors.write(request, response, CashErrorMapping.map(new CashRuleViolation.Forbidden("Only a service may do this")));
                return;
            }
            request.setAttribute(ACTOR_ATTRIBUTE, actor);
            chain.doFilter(request, response);
            return;
        }
        if (!actor.hasTenant()) {
            errors.write(request, response, CashErrorMapping.map(new CashRuleViolation.Forbidden("The token carries no tenant")));
            return;
        }
        if (exceptional != null) {
            // The use is written once the call is let through all the way: a refused call, and a token with no tenant, leave no fact (RN-SEC-012-07).
            String path = request.getRequestURI().substring(request.getContextPath().length());
            try {
                audit.used(new ExceptionalAccessUse(exceptional.authorizationId(), actor.userId(), actor.tenantId(), exceptional.access(),
                        ExceptionalAccessPolicy.areaOf(request.getMethod(), path).orElseThrow(), request.getMethod(), ExceptionalAccessPolicy.template(path),
                        String.valueOf(request.getAttribute(CorrelationFilter.TRACE_ATTRIBUTE))));
            } catch (ExceptionalAccessAuditException unaudited) {
                // Fail closed: a privileged access that cannot be audited is not served (500, standard error, no data).
                errors.write(request, response, CashErrorMapping.map(unaudited));
                return;
            }
        }
        request.setAttribute(ACTOR_ATTRIBUTE, actor);
        chain.doFilter(request, response);
    }
}
