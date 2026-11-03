package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Verifies a compact JWT (RS256 only, configured public key, not expired) and returns the Actor. The key comes from
 * JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE, never from the repository; a failure names only those settings. */
public class Rs256TokenVerifier {
    private final PublicKey key;
    private final Clock clock;
    private final ObjectMapper json;

    public Rs256TokenVerifier(PublicKey key, Clock clock, ObjectMapper json) { this.key = key; this.clock = clock; this.json = json; }

    public static PublicKey loadKey(String inline, String file) {
        try {
            String pem = (inline != null && !inline.isBlank() ? inline.replace("\\n", "\n") : Files.readString(Path.of(file))).strip();
            require(pem.startsWith("-----BEGIN PUBLIC KEY-----") && pem.endsWith("-----END PUBLIC KEY-----"));
            String body = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception failure) {
            throw new IllegalStateException("cannot load an RSA public key from JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE");
        }
    }

    public Actor verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            require(parts.length == 3);
            require("RS256".equals(read(parts[0]).path("alg").textValue()));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            require(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
            JsonNode claims = read(parts[1]);
            require(claims.path("exp").isIntegralNumber() && claims.path("exp").longValue() > clock.instant().getEpochSecond());
            require(claims.path("sub").isTextual() && !claims.path("sub").textValue().isBlank());
            JsonNode tenant = claims.path("tenantId");
            require(tenant.isMissingNode() || tenant.isNull() || tenant.isTextual());
            return new Actor(tenant.isTextual() ? UUID.fromString(tenant.textValue()).toString() : null,
                    claims.path("sub").textValue(), strings(claims.path("roles")), strings(claims.path("permissions")));
        } catch (Exception failure) {
            throw new InvalidTokenException();
        }
    }

    private JsonNode read(String segment) throws Exception {
        JsonNode node = json.readTree(Base64.getUrlDecoder().decode(segment));
        require(node != null && node.isObject());
        return node;
    }

    private static Set<String> strings(JsonNode array) {
        require(array.isMissingNode() || array.isArray());
        Set<String> values = new HashSet<>();
        for (JsonNode value : array) {
            require(value.isTextual());
            values.add(value.textValue());
        }
        return values;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException();
    }
}
