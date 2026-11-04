package co.edu.corhuila.multi_tour.cash_reporting.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RepositoryPortsTest {

    @Test
    void everyRepositoryPortMethodTakesTheTenantFirst()
            throws IOException, URISyntaxException, ClassNotFoundException {
        // The folder of the compiled main classes, not the test folder that shares the package name.
        Path classes = Paths.get(CashSessionRepository.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path directory = classes.resolve(CashSessionRepository.class.getPackageName().replace('.', '/'));
        List<Class<?>> ports = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith("Repository.class"))
                    .toList()) {
                String name = file.getFileName().toString().replace(".class", "");
                ports.add(Class.forName(CashSessionRepository.class.getPackageName() + "." + name));
            }
        }
        assertThat(ports).as("the repository ports found").hasSizeGreaterThanOrEqualTo(2);
        for (Class<?> port : ports) {
            assertThat(port.isInterface()).as(port.getSimpleName()).isTrue();
            assertThat(port.getDeclaredMethods()).as(port.getSimpleName()).isNotEmpty();
            for (Method method : port.getDeclaredMethods()) {
                String where = port.getSimpleName() + "." + method.getName();
                assertThat(method.getParameterCount()).as(where).isGreaterThan(0);
                // javac keeps no names for abstract methods, so the first parameter is checked by its type;
                // when a name is compiled in, it must be tenantId too.
                if (method.getParameters()[0].isNamePresent()) {
                    assertThat(method.getParameters()[0].getName()).as(where).isEqualTo("tenantId");
                }
                assertThat(method.getParameterTypes()[0]).as(where).isEqualTo(String.class);
            }
        }
    }
}
