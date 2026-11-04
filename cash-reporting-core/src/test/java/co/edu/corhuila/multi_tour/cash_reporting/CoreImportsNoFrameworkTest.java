package co.edu.corhuila.multi_tour.cash_reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CoreImportsNoFrameworkTest {

    private static final List<String> FORBIDDEN =
            List.of("org.springframework", "jakarta.", "javax.", "org.hibernate", "com.fasterxml");

    @Test
    void theCoreImportsNoFramework() throws IOException {
        Path sources = Path.of("src/main/java");
        assertThat(sources).isDirectory();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String code = line.strip();
                    if (code.startsWith("import ")) {
                        assertThat(FORBIDDEN).noneMatch(code.substring("import ".length())::startsWith)
                                .as("framework import in %s: %s", file, code);
                    }
                }
            }
        }
        assertThat(Files.readString(Path.of("pom.xml"))).doesNotContain("<artifactId>spring-");
    }
}
