package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The id of each new record: a random UUID. */
@Component
public class UuidIdGenerator implements IdGenerator {

    @Override
    public String next() {
        return UUID.randomUUID().toString();
    }
}
