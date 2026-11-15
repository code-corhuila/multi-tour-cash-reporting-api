package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryTreeTest {

    private static final String PACKAGE = "co/edu/corhuila/multi_tour/cash_reporting";

    private final Path root = RepositoryRules.root();

    @Test
    void repositoryTreeMatchesThePlan() throws IOException {
        for (String module : List.of("core", "adapters", "app")) {
            assertThat(root.resolve("cash-reporting-" + module + "/pom.xml")).isRegularFile();
            assertThat(root.resolve("cash-reporting-" + module + "/src/main/java/" + PACKAGE)).isDirectory();
        }
        for (String file : List.of("pom.xml", "README.md", "Dockerfile", ".env.example", ".gitignore",
                ".github/CODEOWNERS", ".github/workflows/ci.yml")) {
            assertThat(root.resolve(file)).as(file).isRegularFile();
        }
        String pom = Files.readString(root.resolve("pom.xml"));
        assertThat(pom).contains("<module>cash-reporting-core</module>", "<module>cash-reporting-adapters</module>",
                "<module>cash-reporting-app</module>", "<maven.compiler.release>21<");
    }

    @Test
    void theEnvExampleHoldsNamesOnly() throws IOException {
        List<String> lines = Files.readAllLines(root.resolve(".env.example")).stream()
                .filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertThat(lines).isNotEmpty().allMatch(l -> l.matches("[A-Z0-9_]+=") || l.equals("SERVER_PORT=8085"));
    }

    @Test
    void theWorkflowFollowsTheNorm() throws IOException {
        String ci = Files.readString(root.resolve(".github/workflows/ci.yml"));
        assertThat(ci).contains("branches: [develop, qa]", "concurrency:", "timeout-minutes: 10", "mvn -B verify")
                .doesNotContain("schedule");
    }

    @Test
    void theComposeFileDefinesOnlyThisServiceWithoutPortsOrSecrets() throws IOException {
        String compose = Files.readString(root.resolve("deploy/compose.yml"));
        assertThat(compose).contains("cash-reporting-api:", "dockerfile: Dockerfile", "expose: [\"8085\"]",
                "networks: [platform]", "external: true", "${CASH_REPORTING_APP_PASSWORD:?", "${JWT_PUBLIC_KEY:-}",
                "CASH_PERMISSION_MANAGE_CASH", "read_only: true")
                .doesNotContain("ports:", "\n  postgres:", "volumes:");
        assertThat(compose.lines().filter(l -> l.contains("PASSWORD") || l.contains("JWT_PUBLIC_KEY"))
                .filter(l -> !l.trim().startsWith("#")))
                .allMatch(l -> l.contains("${"));
    }
}
