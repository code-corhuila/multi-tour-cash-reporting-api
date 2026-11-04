package co.edu.corhuila.multi_tour.cash_reporting.app;

import java.nio.file.Path;

final class RepositoryRules {

    private RepositoryRules() {
    }

    // The tests run with the module directory as the working directory; the repository root is its parent.
    static Path root() {
        return Path.of("").toAbsolutePath().getParent();
    }
}
