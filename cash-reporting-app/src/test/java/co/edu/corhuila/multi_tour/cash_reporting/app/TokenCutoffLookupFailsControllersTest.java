package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.cash_reporting.usecase.fake.InMemorySessionCutoffs;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** C level, RN-CSH-API-47: a fault of the database while the cutoff is read is never a token let through. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryWorld.class)
class TokenCutoffLookupFailsControllersTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    InMemorySessionCutoffs cutoffs;

    @AfterEach
    void databaseIsBack() {
        cutoffs.failWith(null);
    }

    @Test
    void aFaultWhileReadingTheCutoffIsAFailureAndNeverA200() throws Exception {
        Map<String, Object> claims = new LinkedHashMap<>(TestKeys.claims("TENANT_ADMINISTRATOR"));
        claims.put("sub", UUID.randomUUID().toString());
        String token = TestKeys.sign(Map.of("alg", "RS256"), claims, TestKeys.TRUSTED, "SHA256withRSA");
        cutoffs.failWith(new IllegalStateException("the database is down"));

        int status;
        try {
            status = mvc.perform(get("/api/v1/cash-reporting/cash-days/current").header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getStatus();
        } catch (Exception thrownThroughTheFilter) {
            status = 500;
        }

        assertThat(status).isEqualTo(500);
    }
}
