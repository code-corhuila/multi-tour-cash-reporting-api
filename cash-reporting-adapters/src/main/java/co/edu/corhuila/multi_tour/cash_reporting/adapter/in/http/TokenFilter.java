package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
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

    public TokenFilter(Rs256TokenVerifier verifier, ErrorHandler errors) { this.verifier = verifier; this.errors = errors; }

    public static Actor actorOf(HttpServletRequest request) {
        return (Actor) request.getAttribute(ACTOR_ATTRIBUTE);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "/health".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Actor actor;
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new InvalidTokenException();
            actor = verifier.verify(authorization.substring(7));
        } catch (InvalidTokenException failure) {
            errors.write(request, response, CashErrorMapping.map(failure));
            return;
        }
        if (!actor.hasTenant()) {
            errors.write(request, response, CashErrorMapping.map(new CashRuleViolation.Forbidden("The token carries no tenant")));
            return;
        }
        request.setAttribute(ACTOR_ATTRIBUTE, actor);
        chain.doFilter(request, response);
    }
}
