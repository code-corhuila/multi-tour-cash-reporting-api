package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// Hexagonal layers by a scan of the imports of the sources (ADR-004, norm 5.2). No library: a class of adapter.in.http imports no class of
// adapter.out, the core imports no Spring, JDBC, Jackson or Jakarta, and the core imports no adapter. A new violation fails the test.
class ArchitectureRulesTest {
    private static final String ROOT = "co.edu.corhuila.multi_tour.cash_reporting.";

    private static final List<String> FORBIDDEN_IN_CORE = List.of("org.springframework.", "java.sql.", "javax.sql.", "com.fasterxml.jackson.",
            "jakarta.", "javax.", "org.hibernate", "org.postgresql.", "tools.jackson.");

    private static Path sources(String module) {
        for (String base : List.of("..", ".")) {
            Path path = Path.of(base, module, "src", "main", "java");
            if (Files.isDirectory(path)) return path;
        }
        throw new IllegalStateException("sources of " + module + " not found from " + Path.of("").toAbsolutePath());
    }

    private static List<String> imports(Path file) throws IOException {
        List<String> found = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            String text = line.strip();
            if (!text.startsWith("import ")) continue;
            text = text.substring("import ".length()).replaceFirst("^static ", "");
            found.add(text.endsWith(";") ? text.substring(0, text.length() - 1) : text);
        }
        return found;
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    @Test void theHttpAdaptersImportNoOutAdapter() throws IOException {
        Path http = sources("cash-reporting-adapters").resolve(ROOT.replace('.', '/') + "adapter/in/http");
        List<Path> files = javaFiles(http);
        assertThat(files).hasSizeGreaterThan(20);
        Map<String, List<String>> found = new TreeMap<>();
        for (Path file : files) {
            List<String> violations = imports(file).stream().filter(imported -> imported.startsWith(ROOT + "adapter.out.")).toList();
            if (!violations.isEmpty()) found.put(file.getFileName().toString().replace(".java", ""), violations);
        }

        assertThat(found).as("imports a class of adapter.in.http must not have").isEmpty();
    }

    @Test void theCoreImportsNoFrameworkNoJdbcAndNoJackson() throws IOException {
        List<Path> coreFiles = javaFiles(sources("cash-reporting-core"));
        assertThat(coreFiles).hasSizeGreaterThan(30);
        Map<String, List<String>> found = new TreeMap<>();
        for (Path file : coreFiles) {
            List<String> violations = imports(file).stream().filter(imported -> FORBIDDEN_IN_CORE.stream().anyMatch(imported::startsWith)).toList();
            if (!violations.isEmpty()) found.put(file.getFileName().toString(), violations);
        }

        assertThat(found).isEmpty();
    }

    @Test void theCoreImportsNoAdapter() throws IOException {
        Map<String, List<String>> found = new TreeMap<>();
        for (Path file : javaFiles(sources("cash-reporting-core"))) {
            List<String> violations = imports(file).stream().filter(imported -> imported.startsWith(ROOT + "adapter.")).toList();
            if (!violations.isEmpty()) found.put(file.getFileName().toString(), violations);
        }

        assertThat(found).isEmpty();
    }
}
