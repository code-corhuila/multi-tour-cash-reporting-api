package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.DatabaseProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class HealthDatabaseDownTest {

    @TestConfiguration
    static class DatabaseDown {
        @Bean
        @Primary
        DatabaseProbe databaseDown() {
            return () -> false;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void healthAnswers503WhenTheDatabaseIsDownAndStillNeedsNoToken() throws Exception {
        MvcResult result = mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
                .andReturn();
        ContractCheck.responseMatches(result);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
    }
}
