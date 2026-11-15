package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HU-SEC-007 G-SEC9-1, ADR-004 D5 (areas approved by the Project Lead): the token of an exceptional or emergency access reads only, and every route of this service belongs to the area CASH.
 * Deny by default: any write, the internal routes and a route not listed have no area, so the token never reaches them.
 */
public final class ExceptionalAccessPolicy {
    private static final String BASE = "/api/v1/cash-reporting/";
    private static final Pattern CASH = Pattern.compile(Pattern.quote(BASE) + "(cash-days(/[^/]+(/movements)?)?|cash-closures)");
    private static final Pattern IDENTIFIER = Pattern.compile("(?<=/)[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");

    private ExceptionalAccessPolicy() {
    }

    public static Optional<String> areaOf(String method, String path) {
        if (!"GET".equals(method) || path.contains("..")) return Optional.empty();
        return CASH.matcher(path).matches() ? Optional.of("CASH") : Optional.empty();
    }

    public static boolean allows(String method, String path, Set<String> scope) {
        return areaOf(method, path).filter(scope::contains).isPresent();
    }

    /** The route with its identifiers replaced, safe to write in an audit line. */
    public static String template(String path) {
        return IDENTIFIER.matcher(path).replaceAll("{id}");
    }
}
