package co.edu.corhuila.multi_tour.cash_reporting.port;

/** Outbound port: the id of a new record. */
@FunctionalInterface
public interface IdGenerator {

    String next();
}
