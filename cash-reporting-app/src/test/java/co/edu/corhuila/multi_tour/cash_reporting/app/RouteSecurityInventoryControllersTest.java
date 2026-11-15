package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * K4 (rubric c3-release): the permissions are checked on the server, by an inventory of the routes and not by reading the controllers. Every route
 * that writes (POST, PUT, PATCH, DELETE) is asked without a token and answers 401, and with the token of a role that may not write it answers 403: a
 * role without the permission of the cash for a route of the area, and the role of a Tenant Administrator for a route of the platform (/internal/**),
 * which only accepts SERVICE. A route added later is covered without touching this test; the only public write would be listed here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class RouteSecurityInventoryControllersTest {

    private static final String BASE = "/api/v1/cash-reporting";
    // path (as mapped) -> why it needs no token. Empty today: no route of the api writes without a token.
    private static final Map<String, String> PUBLIC_WRITES = Map.of();

    @Autowired
    private MockMvc mvc;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private record Route(HttpMethod method, String pattern) {
        boolean internal() {
            return pattern.contains("/internal/");
        }

        String path() {
            return pattern.replaceAll("\\{[^/}]+}", "2026-11-03");
        }

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    private Set<Route> writingRoutes() {
        Set<Route> found = new TreeSet<>(Comparator.comparing(Route::toString));
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith(BASE) || pattern.contains("/_probe/")) {
                    continue;
                }
                for (var method : info.getMethodsCondition().getMethods()) {
                    HttpMethod http = HttpMethod.valueOf(method.name());
                    if (http.equals(HttpMethod.POST) || http.equals(HttpMethod.PUT) || http.equals(HttpMethod.PATCH)
                            || http.equals(HttpMethod.DELETE)) {
                        found.add(new Route(http, pattern));
                    }
                }
            }
        }
        return found;
    }

    private MockHttpServletRequestBuilder call(Route route) {
        return request(route.method(), route.path()).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Idempotency-Key", UUID.randomUUID().toString());
    }

    @Test
    void theInventoryFindsTheWritingRoutesOfTheApi() {
        assertThat(writingRoutes()).as("writing routes mapped under " + BASE).hasSizeGreaterThan(8);
    }

    @Test
    void everyWritingRouteRefusesARequestWithoutAToken() throws Exception {
        Map<String, Integer> wrong = new TreeMap<>();
        for (Route route : writingRoutes()) {
            if (PUBLIC_WRITES.containsKey(route.pattern())) {
                continue;
            }
            int code = mvc.perform(call(route)).andReturn().getResponse().getStatus();
            if (code != 401) {
                wrong.put(route.toString(), code);
            }
        }

        assertThat(wrong).as("writing routes that do not answer 401 without a token").isEmpty();
    }

    @Test
    void everyWritingRouteRefusesTheTokenOfARoleThatMayNotWriteIt() throws Exception {
        Map<String, Integer> wrong = new TreeMap<>();
        for (Route route : writingRoutes()) {
            if (PUBLIC_WRITES.containsKey(route.pattern())) {
                continue;
            }
            String role = route.internal() ? "TENANT_ADMINISTRATOR" : "ANALYST";
            int code = mvc.perform(call(route).header("Authorization", "Bearer " + TestKeys.token(role))).andReturn().getResponse().getStatus();
            if (code != 403) {
                wrong.put(route + " with " + role, code);
            }
        }

        assertThat(wrong).as("writing routes that do not answer 403 to a role that may not write them").isEmpty();
    }
}
