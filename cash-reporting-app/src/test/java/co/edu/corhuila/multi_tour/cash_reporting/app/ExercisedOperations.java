package co.edu.corhuila.multi_tour.cash_reporting.app;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * The operations of the contract and the ones the C tests have answered so far: every response validated by
 * {@link ContractCheck} is matched to its {@code operationId} here, and the final gate reads the result.
 */
final class ExercisedOperations {

    /** One operation of {@code openapi.yaml}: its id, the story that owns it and the route it is served at. */
    record Operation(String id, String story, String method, Pattern route) {
    }

    private static final String BASE = "/api/v1/cash-reporting";
    private static final Set<String> EXERCISED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Operation> CONTRACT = load();

    private ExercisedOperations() {
    }

    static Map<String, Operation> contract() {
        return CONTRACT;
    }

    static Set<String> exercised() {
        return Collections.unmodifiableSet(new TreeSet<>(EXERCISED));
    }

    static void record(String method, String path) {
        String relative = path.startsWith(BASE) ? path.substring(BASE.length()) : path;
        CONTRACT.values().stream()
                .filter(operation -> operation.method().equalsIgnoreCase(method) && operation.route().matcher(relative).matches())
                .forEach(operation -> EXERCISED.add(operation.id()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Operation> load() {
        try (Reader reader = Files.newBufferedReader(Path.of("../openapi/openapi.yaml"))) {
            Map<String, Object> paths = (Map<String, Object>) ((Map<String, Object>) new Yaml().load(reader)).get("paths");
            Map<String, Operation> found = new LinkedHashMap<>();
            paths.forEach((path, item) -> ((Map<String, Object>) item).forEach((method, value) -> {
                if (value instanceof Map<?, ?> operation && operation.get("operationId") != null) {
                    String id = operation.get("operationId").toString();
                    Pattern route = Pattern.compile(path.replaceAll("[{][^/}]+[}]", "[^/]+"));
                    found.put(id, new Operation(id, String.valueOf(operation.get("x-hu")), method, route));
                }
            }));
            return found;
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read the contract", failure);
        }
    }
}
