package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

/** The entities live here, not in the package of the application class. */
@Configuration
@EntityScan(basePackageClasses = PersistenceConfiguration.class)
class PersistenceConfiguration {}
