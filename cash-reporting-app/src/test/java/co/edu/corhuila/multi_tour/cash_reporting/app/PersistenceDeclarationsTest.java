package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Domain level (D): the persistence declarations are read from the sources, no database needed. */
class PersistenceDeclarationsTest {

    @Test
    void ddlAutoIsNone() throws IOException {
        Path root = RepositoryRules.root();
        assertThat(Files.readString(root.resolve("cash-reporting-app/src/main/resources/application.yml")))
                .contains("ddl-auto: none");
        try (Stream<Path> files = Files.walk(root.resolve("cash-reporting-adapters/src/main"))) {
            assertThat(files.filter(file -> file.toString().endsWith(".java"))
                            .map(PersistenceDeclarationsTest::read)
                            .filter(text -> text.contains("hbm2ddl") || text.contains("ddl-auto")))
                    .isEmpty();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
