package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * The approved copy lives in multi-tour-docs (main, 07-api/contracts/openapi). CI cannot read that repository, so the
 * check is against a fixed fingerprint of the approved text (line ends normalised); when the contract changes in docs,
 * the copy and these two fingerprints change together in one pull request.
 */
class ContractFileTest {

    private static final String OPENAPI_SHA256 = "29c4911363202c211ab87f91df54fd1e02a9ac7187148beda38ba93e9a807078";
    private static final String SHARED_SHA256 = "4f1e85ed022b709aa058cca677c49997846c53f1225d5239aca318f246ce9100";

    @Test
    void contractFileMatchesTheApprovedCopy() throws Exception {
        assertThat(fingerprint("openapi/openapi.yaml")).isEqualTo(OPENAPI_SHA256);
        assertThat(fingerprint("openapi/_shared.yaml")).isEqualTo(SHARED_SHA256);
    }

    private static String fingerprint(String file) throws IOException, java.security.NoSuchAlgorithmException {
        String text = Files.readString(RepositoryRules.root().resolve(file)).replace("\r\n", "\n");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
