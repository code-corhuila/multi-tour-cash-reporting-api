package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

/** Whether the database answers; /health turns a "no" into 503. */
public interface DatabaseProbe {
    boolean up();
}
