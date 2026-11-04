package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Reuses or generates X-Correlation-Id (response, MDC, traceId) and logs one line: ids, route, status, outcome, time. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String TRACE_ATTRIBUTE = "traceId";
    private static final Logger LOG = LoggerFactory.getLogger(CorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String trace = request.getHeader(HEADER);
        if (trace == null || !trace.matches("[A-Za-z0-9_.-]{1,128}")) trace = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ATTRIBUTE, trace);
        response.setHeader(HEADER, trace);
        MDC.put("traceId", trace);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Object actor = request.getAttribute(TokenFilter.ACTOR_ATTRIBUTE);
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            Object outcome = request.getAttribute(ErrorHandler.OUTCOME_ATTRIBUTE);
            LOG.info("request traceId={} tenantId={} operation={} status={} outcome={} durationMs={}", trace,
                    actor instanceof Actor a ? a.tenantId() : "-", request.getMethod() + " " + (pattern == null ? "unmatched" : pattern),
                    response.getStatus(), outcome == null ? "OK" : outcome, (System.nanoTime() - started) / 1_000_000);
            MDC.remove("traceId");
        }
    }
}
