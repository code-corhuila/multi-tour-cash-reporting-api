package co.edu.corhuila.multi_tour.cash_reporting.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "co.edu.corhuila.multi_tour.cash_reporting")
public class CashReportingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashReportingApplication.class, args);
    }
}
