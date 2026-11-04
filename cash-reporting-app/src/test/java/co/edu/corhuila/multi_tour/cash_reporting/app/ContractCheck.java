package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import java.nio.file.Path;
import org.springframework.test.web.servlet.MvcResult;

/** Validates a response against the approved copy of the contract kept in {@code openapi/} (ADR-004 D7). */
final class ContractCheck {

    private static final String SPEC = Path.of("../openapi/openapi.yaml").toAbsolutePath().toUri().toString();
    private static final OpenApiInteractionValidator API = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC).withBasePathOverride("/api/v1/cash-reporting").build();
    private static final OpenApiInteractionValidator ROOT = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC).withBasePathOverride("").build();

    private ContractCheck() {
    }

    static void responseMatches(MvcResult result) {
        var request = MockMvcRequest.of(result.getRequest());
        var validator = request.getPath().equals("/health") ? ROOT : API;
        var report = validator.validateResponse(request.getPath(), request.getMethod(),
                MockMvcResponse.of(result.getResponse()));
        assertThat(report.hasErrors()).as("response contract: %s", report).isFalse();
        ExercisedOperations.record(request.getMethod().name(), request.getPath());
    }
}
