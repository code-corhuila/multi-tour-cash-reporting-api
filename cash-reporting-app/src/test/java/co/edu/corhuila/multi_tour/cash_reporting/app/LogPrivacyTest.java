package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** RNF-A1.4: everything every logger wrote during a real run of the routes, scanned for what must stay private. */
class LogPrivacyTest extends PrivacyRun {

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void capture() {
        lines.start();
        root.addAppender(lines);
    }

    @AfterEach
    void release() {
        root.detachAppender(lines);
    }

    @Test
    void logsCarryNoConceptReasonAmountOrToken() throws Exception {
        List<String> tokens = run();

        List<String> everything = new ArrayList<>();
        for (ILoggingEvent event : lines.list) {
            everything.add(event.getFormattedMessage());
            everything.addAll(event.getMDCPropertyMap().values());
            for (IThrowableProxy error = event.getThrowableProxy(); error != null; error = error.getCause()) {
                everything.add(error.getMessage());
            }
        }
        assertThat(everything.stream().filter(line -> line != null && line.startsWith("request traceId=")).count())
                .as("one line per request of the run").isGreaterThanOrEqualTo(12);
        String scanned = String.join("\n", everything);
        assertThat(scanned).doesNotContain(CONCEPT, "Zorro", REASON, "reservado", BASE_CENTS, AMOUNT_CENTS)
                .doesNotContain(tokens).doesNotContain("Bearer");
    }
}
