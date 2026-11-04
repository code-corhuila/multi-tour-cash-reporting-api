package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;

/** The use cases name a cash day by its date; the key and event tables hold the id of the cash of that day. */
final class ResourceIds {

    private ResourceIds() {}

    /** The uuid in the text, or the id of the cash of the date in the text. */
    static UUID toUuid(EntityManager em, String tenantId, String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAnId) {
            return JpaCashSessionRepository.idOf(em, tenantId, BusinessDate.of(LocalDate.parse(text)));
        }
    }
}
