package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.ExceptionalAccessAudit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-SEC-007 G-SEC9-1 (ADR-004 D5): a token with access=exceptional or emergency is accepted only on a read inside its scope, is refused with 403 on any write and with 401 when its claims are
 * incomplete, and every use that is let through is audited with the authorizationId through the port.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({InMemoryWorld.class, ExceptionalAccessControllersTest.Recording.class})
class ExceptionalAccessControllersTest {

    private static final String BASE = "/api/v1/cash-reporting";
    private static final UUID AUTHORIZATION = UUID.randomUUID();

    @TestConfiguration
    static class Recording {
        @Bean
        @Primary
        Uses uses() {
            return new Uses();
        }
    }

    static class Uses implements ExceptionalAccessAudit {
        final List<String> recorded = new ArrayList<>();

        @Override
        public void used(UUID authorizationId, String access, String area, String method, String routeTemplate) {
            recorded.add(authorizationId + "|" + access + "|" + area + "|" + method + "|" + routeTemplate);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private Uses uses;

    @BeforeEach
    void clean() {
        uses.recorded.clear();
    }

    private String token(String access, Object scope, Object authorizationId) {
        Map<String, Object> claims = TestKeys.claims("PLATFORM_ADMINISTRATOR");
        claims.put("tenantId", UUID.randomUUID().toString());
        if (access != null) claims.put("access", access);
        if (scope != null) claims.put("scope", scope);
        if (authorizationId != null) claims.put("authorizationId", authorizationId);
        return TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
    }

    private int answer(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }

    @Test
    void aReadInsideTheScopeIsLetThroughAndAudited() throws Exception {
        // The mark of the token makes the actor the read only PLATFORM_SUPPORT: the route answers the read and the use is audited.
        assertThat(answer(get(BASE + "/cash-days"), token("exceptional", List.of("CASH"), AUTHORIZATION.toString()))).isEqualTo(200);

        assertThat(uses.recorded).containsExactly(AUTHORIZATION + "|exceptional|CASH|GET|" + BASE + "/cash-days");
    }

    @Test
    void withoutTheMarkThePlatformAdministratorAndAForgedSupportRoleAreRefused() throws Exception {
        for (String role : List.of("PLATFORM_ADMINISTRATOR", "PLATFORM_SUPPORT")) {
            Map<String, Object> claims = TestKeys.claims(role);
            claims.put("tenantId", UUID.randomUUID().toString());
            String bearer = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");

            assertThat(answer(get(BASE + "/cash-days"), bearer)).as(role).isEqualTo(403);
        }
        assertThat(uses.recorded).isEmpty();
    }

    @Test
    void aReadOutsideTheScopeOrOnAnInternalRouteIsRefusedAndNotRecordedAsAUse() throws Exception {
        String bearer = token("exceptional", List.of("RESERVATIONS", "CUSTOMERS"), AUTHORIZATION.toString());

        mvc.perform(get(BASE + "/cash-days").header("Authorization", "Bearer " + bearer)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        assertThat(answer(get(BASE + "/internal/cash-days/open"), token("emergency", List.of("CASH"), AUTHORIZATION.toString()))).isEqualTo(403);
        assertThat(uses.recorded).isEmpty();
    }

    @Test
    void anyWriteIsRefusedWithForbiddenEvenInsideTheScope() throws Exception {
        String bearer = token("emergency", List.of("CASH", "RESERVATIONS"), AUTHORIZATION.toString());

        assertThat(answer(post(BASE + "/cash-days/2026-11-03/open"), bearer)).isEqualTo(403);
        assertThat(answer(post(BASE + "/cash-days/2026-11-03/movements"), bearer)).isEqualTo(403);
        assertThat(answer(post(BASE + "/cash-days/2026-11-03/close"), bearer)).isEqualTo(403);
        assertThat(uses.recorded).isEmpty();
    }

    @Test
    void aTokenWithAnExceptionalAccessButIncompleteOrBadClaimsIsUnauthorized() throws Exception {
        assertThat(answer(get(BASE + "/cash-days"), token("exceptional", null, AUTHORIZATION.toString()))).isEqualTo(401);
        assertThat(answer(get(BASE + "/cash-days"), token("exceptional", List.of("CASH"), null))).isEqualTo(401);
        assertThat(answer(get(BASE + "/cash-days"), token("exceptional", List.of("CASH"), "not-a-uuid"))).isEqualTo(401);
        assertThat(answer(get(BASE + "/cash-days"), token("exceptional", "CASH", AUTHORIZATION.toString()))).isEqualTo(401);
        assertThat(answer(get(BASE + "/cash-days"), token("exceptional", List.of("EVERYTHING"), AUTHORIZATION.toString()))).isEqualTo(401);
        assertThat(answer(get(BASE + "/cash-days"), token("exceptional", List.of(), AUTHORIZATION.toString()))).isEqualTo(401);
        assertThat(uses.recorded).isEmpty();
    }
}
