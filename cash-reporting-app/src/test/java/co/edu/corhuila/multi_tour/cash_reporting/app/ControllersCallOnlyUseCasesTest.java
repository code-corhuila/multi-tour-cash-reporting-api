package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** A controller translates and calls a use case: it never holds a repository or a key store (hexagonal layers). */
class ControllersCallOnlyUseCasesTest {

    private static final String HTTP = "cash-reporting-adapters/src/main/java/co/edu/corhuila/multi_tour/cash_reporting/adapter/in/http";

    @Test
    void noControllerImportsAnOutboundPortOtherThanThePagingTypes() throws IOException {
        List<Path> controllers;
        try (Stream<Path> files = Files.list(RepositoryRules.root().resolve(HTTP))) {
            controllers = files.filter(file -> file.getFileName().toString().endsWith("Controller.java")).toList();
        }
        assertThat(controllers).isNotEmpty();
        for (Path controller : controllers) {
            List<String> ports = Files.readAllLines(controller).stream()
                    .filter(line -> line.startsWith("import co.edu.corhuila.multi_tour.cash_reporting.port."))
                    .filter(line -> !line.endsWith(".PageRequest;") && !line.endsWith(".Page;"))
                    .toList();
            assertThat(ports).as(controller.getFileName().toString()).isEmpty();
        }
    }
}
