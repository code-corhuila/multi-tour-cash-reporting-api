package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoDdlTest {

    private static final Pattern DDL_AUTO = Pattern.compile("ddl-auto:\s*(\S+)");

    @Test
    void theApiHoldsNoDdlAndNoGeneratedSchema() throws IOException {
        Path root = RepositoryRules.root();
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(f -> !f.toString().contains("target") && !f.toString().contains(".git"))
                    .filter(f -> f.toString().endsWith(".sql"))).isEmpty();
        }
        String config = Files.readString(root.resolve("cash-reporting-app/src/main/resources/application.yml"));
        Matcher ddl = DDL_AUTO.matcher(config);
        assertThat(ddl.find()).as("ddl-auto must be declared").isTrue();
        do {
            assertThat(ddl.group(1)).isEqualTo("none");
        } while (ddl.find());
    }
}
