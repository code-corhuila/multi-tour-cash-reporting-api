package co.edu.corhuila.multi_tour.cash_reporting.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * The keys exist only inside the test process: nothing that signs is read from, or written to, the repository. Registered
 * in META-INF/spring.factories so every Spring test of this module starts with a trusted public key and the test
 * constant of the "Gestionar caja" permission (G-PERM) without repeating the setup.
 */
public class TestKeys implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final String TENANT = "00000000-0000-0000-0000-000000000123";
    static final String ACCOUNT = "00000000-0000-0000-0000-000000000456";
    static final String MANAGE_CASH = "test.manage-cash";
    static final KeyPair TRUSTED = generate();
    static final KeyPair OTHER = generate();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testKeys", Map.of(
                "JWT_PUBLIC_KEY", pem(TRUSTED), "JWT_PUBLIC_KEY_FILE", "",
                "cash.permission.manage-cash", MANAGE_CASH)));
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException("cannot generate test keys", failure);
        }
    }

    static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    static Map<String, Object> claims(String role, String... permissions) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", ACCOUNT);
        claims.put("exp", NOW.plusSeconds(300).getEpochSecond());
        claims.put("tenantId", TENANT);
        claims.put("roles", List.of(role));
        claims.put("permissions", List.of(permissions));
        return claims;
    }

    static String token(String role, String... permissions) {
        return sign(Map.of("alg", "RS256"), claims(role, permissions), TRUSTED, "SHA256withRSA");
    }

    static String sign(Map<String, Object> header, Map<String, Object> claims, KeyPair signer, String algorithm) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String input = encoder.encodeToString(JSON.writeValueAsBytes(header)) + "."
                    + encoder.encodeToString(JSON.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(signer.getPrivate());
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + encoder.encodeToString(signature.sign());
        } catch (Exception failure) {
            throw new IllegalStateException("cannot sign a test token", failure);
        }
    }
}
