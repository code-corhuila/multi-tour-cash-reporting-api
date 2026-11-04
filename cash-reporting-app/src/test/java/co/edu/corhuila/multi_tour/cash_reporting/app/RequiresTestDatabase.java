package co.edu.corhuila.multi_tour.cash_reporting.app;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The class runs only when the three TEST_DATABASE variables are set; without them it is skipped (spec DD-6). */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_PASSWORD", matches = ".+")
@interface RequiresTestDatabase {}
